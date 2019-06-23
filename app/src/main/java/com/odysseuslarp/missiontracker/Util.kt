package com.odysseuslarp.missiontracker

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.Style

const val MY_STYLE = Style.SATELLITE

fun getMissionBounds(mission: DocumentSnapshot) = mission.run {
    getGeoPoint("bounds_nw")?.let { nw ->
        getGeoPoint("bounds_se")?.let { se ->
            LatLngBounds.from(nw.latitude, se.longitude, se.latitude, nw.longitude)
        }
    }
}

fun GeoPoint.toPoint() = Point.fromLngLat(longitude, latitude)