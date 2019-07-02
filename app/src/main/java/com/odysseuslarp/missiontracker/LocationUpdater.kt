package com.odysseuslarp.missiontracker

import android.os.SystemClock
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.*

private const val MIN_INTERVAL_MILLIS = 3000L

object LocationUpdater {
    private val locationsDocument = FirebaseFirestore.getInstance().document("missiondata/locations")

    var location: GeoPoint? = null
        set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    onNewLocation()
                }
            }
        }

    private var currentUpdateJob: Job? = null
    private var hasNewLocation = false
    private fun onNewLocation() {
        hasNewLocation = true
        updateLocationIfNeeded()
    }
    private fun updateLocationIfNeeded() {
        if (hasNewLocation && currentUpdateJob == null) {
            hasNewLocation = false
            location?.let {
                val earliestNextUpdateTime = SystemClock.elapsedRealtime() + MIN_INTERVAL_MILLIS
                currentUpdateJob = MainScope().launch {
                    val success = locationsDocument.updateAsync("team", it).await()
                    delay(earliestNextUpdateTime - SystemClock.elapsedRealtime())
                    if (!success) hasNewLocation = true
                    currentUpdateJob = null
                    updateLocationIfNeeded()
                }
            }
        }
    }
}

private fun DocumentReference.updateAsync(field: String, value: Any?): Deferred<Boolean> {
    return CompletableDeferred<Boolean>().apply {
        update(field, value)
            .addOnSuccessListener { complete(true) }
            .addOnFailureListener { complete(false) }
    }
}