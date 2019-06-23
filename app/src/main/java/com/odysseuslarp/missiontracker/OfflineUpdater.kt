package com.odysseuslarp.missiontracker

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.offline.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class OfflineUpdater(context: Context) {

    var active: Boolean
        get() = missionsListenerRegistration != null
        set(value) {
            if (value) {
                if (!failed && missionsListenerRegistration == null) missionsListenerRegistration = addMissionsListener()
            } else {
                missionsListenerRegistration?.remove()
                missionsListenerRegistration = null
            }
        }

    private var failed = false
        set(value) {
            field = value
            if (!value) active = false
        }

    private val offlineManager = OfflineManager.getInstance(context)
    private val pixelRatio = context.resources.displayMetrics.density

    private var missionsListenerRegistration: ListenerRegistration? = null

    private var regions: ArrayList<OfflineRegion>? = null
    private var missionBounds: List<LatLngBounds>? = null

    private var needsUpdateOfflineRegions = false
    private var updateOfflineRegionsJob: Job? = null

    init {
        MainScope().launch {
            try {
                regions = arrayListOf(*offlineManager.listOfflineRegions()).apply {
                    forEach { it.setObserver(RegionObserver) }
                }
                setNeedsUpdateOfflineRegions()
            } catch (_: Exception) {
                failed = true
            }
        }
    }


    private fun addMissionsListener() = FirebaseFirestore.getInstance()
            .collection("missions")
            .addSnapshotListener { querySnapshot, _ ->
                querySnapshot?.let {
                    missionBounds = it.mapNotNull(::getMissionBounds)
                    setNeedsUpdateOfflineRegions()
                }
            }

    private fun setNeedsUpdateOfflineRegions() {
        needsUpdateOfflineRegions = true
        updateOfflineRegionsIfNeeded()
    }

    private fun updateOfflineRegionsIfNeeded() {
        if (active && needsUpdateOfflineRegions && updateOfflineRegionsJob == null) {
            updateOfflineRegionsJob = MainScope().launch {
                updateOfflineRegions()
                updateOfflineRegionsJob = null
                updateOfflineRegionsIfNeeded()
            }
        }
    }

    private suspend fun updateOfflineRegions() {
        val regions = regions ?: return
        val missionBounds = missionBounds ?: return

        regions.iterator().apply {
            while (hasNext()) {
                next().apply {
                    if (missionBounds.any(::matchesMissionBounds)) {
                        activateIfNeeded()
                    } else {
                        remove()
                        delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {}
                            override fun onError(error: String?) {
                                Log.e("OfflineUpdater", "delete error $error")
                            }
                        })
                    }
                }
            }
        }

        for (bounds in missionBounds) {
            if (regions.none { it.matchesMissionBounds(bounds) }) {
                val definition = OfflineTilePyramidRegionDefinition(MY_STYLE, bounds, 0.0, 18.0, pixelRatio)
                try {
                    val region = offlineManager.createOfflineRegion(definition)
                    region.setObserver(RegionObserver)
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    regions.add(region)
                } catch (e: Exception) {
                    Log.e("OfflineUpdater", "create error $e")
                }
            }
        }
    }

    private object RegionObserver : OfflineRegion.OfflineRegionObserver {
        override fun mapboxTileCountLimitExceeded(limit: Long) {
            Log.w("OfflineUpdater", "tile limit exceeded $limit")
        }

        override fun onStatusChanged(status: OfflineRegionStatus?) {
            if (status?.isComplete == true) {
                Log.i("OfflineUpdater", "region loaded")
            }
        }

        override fun onError(error: OfflineRegionError?) {
            Log.e("OfflineUpdater", "region error $error")
        }
    }
}

private suspend fun OfflineManager.listOfflineRegions(): Array<out OfflineRegion> {
    return CompletableDeferred<Array<out OfflineRegion>>().apply {
        listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<out OfflineRegion>) {
                complete(offlineRegions)
            }
            override fun onError(error: String?) {
                completeExceptionally(Exception(error))
            }
        })
    }.await()
}

private suspend fun OfflineManager.createOfflineRegion(definition: OfflineRegionDefinition): OfflineRegion {
    return CompletableDeferred<OfflineRegion>().apply {
        createOfflineRegion(definition, byteArrayOf(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                complete(offlineRegion)
            }
            override fun onError(error: String?) {
                completeExceptionally(Exception(error))
            }
        })
    }.await()
}

private fun OfflineRegion.activateIfNeeded() {
    getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
        override fun onStatus(status: OfflineRegionStatus?) {
            if (status?.isComplete == false) {
                setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
        }

        override fun onError(error: String?) {
            Log.e("OfflineUpdater", "getStatus error $error")
        }
    })
}

private fun OfflineRegion.matchesMissionBounds(missionBounds: LatLngBounds): Boolean {
    val definition = definition as? OfflineTilePyramidRegionDefinition
    return definition == missionBounds
}
