package com.odysseuslarp.missiontracker

import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.firestore.GeoPoint
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult

class LastLocationLiveData : LiveData<GeoPoint>(), LocationEngineCallback<LocationEngineResult> {
    var locationEngine: LocationEngine? = null
        set(value) {
            if (!BuildConfig.MOCK_ENABLED) {
                field?.removeLocationUpdates(this)
                field = value
                if (hasActiveObservers()) {
                    startLocationUpdates()
                }
            }
        }

    fun setMockLocation(geoPoint: GeoPoint) {
        if (BuildConfig.MOCK_ENABLED) {
            value = geoPoint
        }
    }

    override fun onActive() {
        super.onActive()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            locationEngine?.requestLocationUpdates(
                LocationEngineRequest.Builder(1000L).build(),
                this,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LastLocation", "Could not start location updates", e)
        }
    }

    override fun onInactive() {
        super.onInactive()
        locationEngine?.removeLocationUpdates(this)
    }

    override fun onSuccess(result: LocationEngineResult?) {
        result?.lastLocation?.run {
            value = GeoPoint(latitude, longitude)
        }
    }

    override fun onFailure(exception: Exception) {
        Log.e("LastLocation", "Location error", exception)
    }
}