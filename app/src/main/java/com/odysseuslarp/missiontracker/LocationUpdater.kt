package com.odysseuslarp.missiontracker

import android.os.SystemClock
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.*

private const val MIN_INTERVAL_MILLIS = 3000L

object LocationUpdater {
    private val locationsDocument = FirebaseFirestore.getInstance().document("missiondata/locations")

    private val teamMemberLocationsCollection = if (BuildConfig.TEAM_MEMBER_APP) locationsDocument.collection("team_members") else null

    private var firebaseId: String? = null

    fun setFirebaseId(firebaseId: String?) {
        if (BuildConfig.TEAM_MEMBER_APP) {
            this.firebaseId = firebaseId
            updateLocationIfNeeded()
        }
    }

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
        if (shouldUpdateLocation()) {
            hasNewLocation = false
            location?.let {
                val earliestNextUpdateTime = SystemClock.elapsedRealtime() + MIN_INTERVAL_MILLIS
                currentUpdateJob = MainScope().launch {
                    val success = updateLocation(it)
                    delay(earliestNextUpdateTime - SystemClock.elapsedRealtime())
                    if (!success) hasNewLocation = true
                    currentUpdateJob = null
                    updateLocationIfNeeded()
                }
            }
        }
    }

    private fun shouldUpdateLocation(): Boolean {
        return hasNewLocation && currentUpdateJob == null && (!BuildConfig.TEAM_MEMBER_APP || firebaseId != null)
    }

    private suspend fun updateLocation(location: GeoPoint): Boolean {
        return if (BuildConfig.TEAM_MEMBER_APP) {
            firebaseId?.let {
                teamMemberLocationsCollection
                    ?.document(it)
                    ?.setAsync(mapOf("location" to location))
                    ?.await()
            } == true
        } else {
            locationsDocument.updateAsync("team", location).await()
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

private fun DocumentReference.setAsync(data: Any): Deferred<Boolean> {
    return CompletableDeferred<Boolean>().apply {
        set(data)
            .addOnSuccessListener { complete(true) }
            .addOnFailureListener { complete(false) }
    }
}