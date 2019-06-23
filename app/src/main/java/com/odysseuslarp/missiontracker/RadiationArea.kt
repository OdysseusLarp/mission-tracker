package com.odysseuslarp.missiontracker

import android.location.Location
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val POLYGON_VERTICES = 64

class RadiationArea(
    val center: GeoPoint,
    val radiusMeters: Double,
    val intensity: Double
) {
    companion object {
        const val COLOR_PROPERTY = "color"

        fun fromDocumentSnapshot(documentSnapshot: DocumentSnapshot) = documentSnapshot.run {
            getGeoPoint("center")?.let { center ->
                getDouble("radius")?.let { radiusMeters ->
                    getDouble("intensity")?.let { intensity ->
                        RadiationArea(center, radiusMeters, intensity)
                    }
                }
            }
        }
    }
}

private fun RadiationArea.toGeoJsonPolygon(): Polygon {
    val centerLat = center.latitude
    val centerLon = center.longitude
    val distanceLat = radiusMeters * (1.0 / 110574.0)
    val distanceLon = radiusMeters * (1.0 / 111320.0) / cos(centerLat * (PI / 180.0))
    val angleStep = 2.0 * PI / POLYGON_VERTICES
    val vertices = (0 until POLYGON_VERTICES).mapTo(ArrayList(POLYGON_VERTICES + 1)) {
        val angle = angleStep * it
        val lat = centerLat + distanceLat * sin(angle)
        val lon = centerLon + distanceLon * cos(angle)
        Point.fromLngLat(lon, lat)
    }
    vertices.add(vertices.first())

    return Polygon.fromLngLats(listOf(vertices))
}

fun RadiationArea.toFeature() = Feature.fromGeometry(toGeoJsonPolygon(), JsonObject().apply {
    addProperty(RadiationArea.COLOR_PROPERTY, when {
        intensity <= 1 -> "#FFA500"
        intensity <= 2 -> "#FF7700"
        else -> "#FF0000"
    })
})

fun RadiationArea.contains(location: GeoPoint): Boolean {
    val distance = floatArrayOf(0f)
    Location.distanceBetween(center.latitude, center.longitude, location.latitude, location.longitude, distance)
    return distance[0] <= radiusMeters
}