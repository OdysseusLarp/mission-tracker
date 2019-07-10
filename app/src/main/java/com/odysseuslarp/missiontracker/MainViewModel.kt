package com.odysseuslarp.missiontracker

import androidx.lifecycle.*
import com.google.firebase.firestore.*
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point

private fun <T> nullLiveData() = MutableLiveData<T>().apply { value = null }

const val FEATURE_TEAM_MEMBER_ICON = "icon"

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
    private val teamMembersCollection = QueryLiveData(db.collection("missiondata/locations/team_members"))
    private val sortedTeamMembers = Transformations.map(teamMembersCollection) { it?.sortedBy(QueryDocumentSnapshot::getId) }

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
    val team = if (BuildConfig.COMMAND_APP || BuildConfig.TEAM_MEMBER_APP) {
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

    val locationColor: LiveData<Int>? = if (BuildConfig.TEAM_MEMBER_APP) {
        object : MediatorLiveData<Int>(), Observer<Any?> {
            init {
                addSource(FirebaseIdLiveData, this)
                addSource(sortedTeamMembers, this)
            }

            override fun onChanged(t: Any?) {
                value = FirebaseIdLiveData.value?.let { myId ->
                    sortedTeamMembers.value?.indexOfFirst { it.id == myId }
                }?.let { index ->
                    MainActivity.TEAM_MEMBER_COLORS.let { it.getOrNull(index % it.size) }
                } ?: MainActivity.TEAM_LEADER_COLOR
            }
        }
    } else {
        null
    }

    val teamMemberFeatures: LiveData<FeatureCollection> = object : MediatorLiveData<FeatureCollection>(), Observer<Any?> {
        init {
            addSource(sortedTeamMembers, this)
            if (BuildConfig.TEAM_MEMBER_APP) addSource(FirebaseIdLiveData, this)
        }

        override fun onChanged(t: Any?) {
            val features = sortedTeamMembers.value?.withIndex()?.let { teamMembers ->
                if (BuildConfig.TEAM_MEMBER_APP) {
                    FirebaseIdLiveData.value?.let { myId ->
                        teamMembers.mapNotNull { teamMember ->
                            teamMember.takeUnless { it.value.id == myId }?.let(::teamMemberToFeature)
                        }
                    }
                } else {
                    teamMembers.mapNotNull(::teamMemberToFeature)
                }
            }
            value = FeatureCollection.fromFeatures(features ?: emptyList())
        }
    }

    private fun teamMemberToFeature(teamMember: IndexedValue<DocumentSnapshot>) =
        (teamMember.value.get("location") as? GeoPoint)?.let { location ->
            Feature.fromGeometry(
                location.toPoint(),
                JsonObject().apply {
                    addProperty(FEATURE_TEAM_MEMBER_ICON, getTeamMemberIcon(teamMember.index))
                }
            )
        }

    private fun getTeamMemberIcon(index: Int) =
        MainActivity.getTeamMemberImageName(index % MainActivity.TEAM_MEMBER_COLORS.size)
}
