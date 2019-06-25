package com.odysseuslarp.missiontracker

import androidx.lifecycle.*
import com.google.firebase.firestore.*
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point

private fun <T> nullLiveData() = MutableLiveData<T>().apply { value = null }

class MainViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val state = DocumentLiveData(db.document("missiondata/state"))
    private val currentMissionReference = Transformations.map(state) {
        it?.get("current_mission") as? DocumentReference
    }
    val currentMission = Transformations.switchMap(currentMissionReference) {
        it?.let(::DocumentLiveData) ?: nullLiveData<DocumentSnapshot>()
    }
    private val locations = DocumentLiveData(db.document("missiondata/locations"))
    private val dynamicTargetLocation = Transformations.map(locations) {
        (it?.get("target") as? GeoPoint)?.toPoint()
    }
    private val radiationCollection = Transformations.switchMap(currentMissionReference) {
        it?.collection("radiation")?.let(::QueryLiveData) ?: nullLiveData<QuerySnapshot>()
    }

    val currentBounds = Transformations.map(currentMission) {
        it?.let(::getMissionBounds)
    }
    val target = Transformations.switchMap(currentMission) {
        if (it?.get("target_disabled") == true) {
            nullLiveData()
        } else when (val target = it?.get("target")) {
            is GeoPoint -> MutableLiveData<Point?>().apply { value = target.toPoint() }
            "dynamic" -> dynamicTargetLocation
            else -> nullLiveData()
        }
    }
    val team = if (BuildConfig.COMMAND_APP) {
        Transformations.map(locations) {
            it?.get("team") as? GeoPoint
        }
    } else {
        null
    }
    val radiationAreas = Transformations.map(radiationCollection) {
        it?.mapNotNull(RadiationArea.Companion::fromDocumentSnapshot)
    }
    val radiationFeatures = Transformations.map(radiationAreas) {
        FeatureCollection.fromFeatures(it?.map(RadiationArea::toFeature) ?: emptyList())
    }
    val lastLocation = if (!BuildConfig.COMMAND_APP) LastLocationLiveData() else null
    val radiationWarning: LiveData<Double?> = object : MediatorLiveData<Double?>(), Observer<Any?> {
        private val locationSource = lastLocation ?: team
        init {
            addSource(radiationAreas, this)
            locationSource?.let { addSource(it, this) }
            value = calcValue()
        }
        override fun onChanged(t: Any?) {
            val value = calcValue()
            if (value != this.value) {
                this.value = value
            }
        }
        private fun calcValue(): Double? {
            return locationSource?.value?.let { location ->
                radiationAreas.value?.filter { it.contains(location) }?.map(RadiationArea::intensity)?.max()
            }
        }
    }
}
