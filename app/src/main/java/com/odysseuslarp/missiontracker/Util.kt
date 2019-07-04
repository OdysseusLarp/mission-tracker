package com.odysseuslarp.missiontracker

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.Style
import java.lang.RuntimeException

const val MY_STYLE = Style.SATELLITE

fun getMissionBounds(mission: DocumentSnapshot) = mission.run {
    try {
        getGeoPoint("bounds_nw")?.let { nw ->
            getGeoPoint("bounds_se")?.let { se ->
                LatLngBounds.from(nw.latitude, se.longitude, se.latitude, nw.longitude)
            }
        }
    } catch (_: RuntimeException) {
        null
    }
}

fun GeoPoint.toPoint() = Point.fromLngLat(longitude, latitude)

private fun LatLng.toPoint() = Point.fromLngLat(longitude, latitude)

fun LatLngBounds.toPolygon(): Polygon {
    val sw = southWest.toPoint()
    return Polygon.fromLngLats(
        listOf(
            listOf(
                sw,
                southEast.toPoint(),
                northEast.toPoint(),
                northWest.toPoint(),
                sw
            )
        )
    )
}