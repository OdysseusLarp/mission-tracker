package com.odysseuslarp.missiontracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

private const val LOCATION_PERMISSION_REQUEST_CODE = 1

private const val TARGET_SOURCE_ID = "target_src"
private const val TARGET_LAYER_ID = "target_layer"
private const val TARGET_IMAGE = "target_img"

private const val TEAM_SOURCE_ID = "team_src"
private const val TEAM_LAYER_ID = "team_layer"
private const val TEAM_IMAGE = "team_img"

private const val RADIATION_SOURCE_ID = "rad_src"
private const val RADIATION_LAYER_ID = "rad_layer"
private const val RADIATION_OPACITY = 0.7f

private const val MOCK_SOURCE_ID = "mock_src"
private const val MOCK_LAYER_ID = "mock_layer"
private const val MOCK_IMAGE = "mock_img"

private const val BOUNDS_SOURCE_ID = "bounds_src"
private const val BOUNDS_LAYER_ID = "bounds_layer"
private const val BOUNDS_COLOR = "rgba(160, 0, 255, 0.7)"

class MainActivity : AppCompatActivity() {

    private val vm by lazy { ViewModelProviders.of(this)[MainViewModel::class.java] }
    private val mapView by lazy { findViewById<MapView>(R.id.mapView) }
    private val radiationWarning by lazy { findViewById<View>(R.id.rad_warning) }
    private val radiationWarningText by lazy { findViewById<View>(R.id.rad_warning_text) }
    private val inactiveCover by lazy { findViewById<View>(R.id.inactive_cover) }
    private val offlineUpdater by lazy { if (!BuildConfig.COMMAND_APP) OfflineUpdater(this) else null }

    private val teamSource by lazy { GeoJsonSource(TEAM_SOURCE_ID) }
    private val targetSource by lazy { GeoJsonSource(TARGET_SOURCE_ID) }
    private val radiationSource by lazy { GeoJsonSource(RADIATION_SOURCE_ID) }

    private val mockSource by lazy { if (BuildConfig.MOCK_ENABLED) GeoJsonSource(MOCK_SOURCE_ID) else null }

    private val boundsSource by lazy { if (BuildConfig.ADMIN_MONITOR) GeoJsonSource(BOUNDS_SOURCE_ID) else null }

    private val locationComponentWrangler = if (!BuildConfig.COMMAND_APP) LocationComponentWrangler() else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, "pk.eyJ1IjoicGVydHRpIiwiYSI6ImNpc3JpNTRyMjAwM3UydGs2Ymw3M3pqZTgifQ.90QQ7GIrcKCp3OfDuXtvYA")
        offlineUpdater?.active = true

        setContentView(R.layout.activity_main)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(::onMapReady)

        radiationWarningText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rad_warning_blink))

        vm.currentMission.observe(this, Observer {
            inactiveCover.visibility = if (it != null) View.INVISIBLE else View.VISIBLE
        })
        vm.target.observe(this, Observer {
            if (it != null) {
                targetSource.setGeoJson(it)
            } else {
                targetSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            }
        })
        vm.radiationFeatures.observe(this, Observer {
            it?.let(radiationSource::setGeoJson)
        })
        vm.radiationWarning.observe(this, Observer {
            radiationWarning.visibility = if (it != null) View.VISIBLE else View.INVISIBLE
        })

        if (BuildConfig.COMMAND_APP) {
            vm.team?.observe(this, Observer {
                if (it != null) {
                    teamSource.setGeoJson(it.toPoint())
                } else {
                    teamSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                }
            })
        } else {
            vm.lastLocation?.observe(this, Observer {
                LocationUpdater.location = GeoPoint(it.latitude, it.longitude)
            })
        }

        if (BuildConfig.MOCK_ENABLED) {
            vm.lastLocation?.observe(this, Observer {
                it?.apply {
                    mockSource?.setGeoJson(Point.fromLngLat(longitude, latitude))
                }
            })
        }

        if (BuildConfig.ADMIN_MONITOR) {
            vm.currentBounds.observe(this, Observer {
                if (it != null) {
                    boundsSource?.setGeoJson(it.toPolygon())
                } else {
                    boundsSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                }
            })
        }
    }

    private fun onMapReady(mapboxMap: MapboxMap) {
        if (BuildConfig.MOCK_ENABLED) {
            mapboxMap.addOnMapClickListener {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                vm.lastLocation?.setMockLocation(geoPoint)
                true
            }
        }
        mapboxMap.setStyle(buildStyle()) {
            onStyleLoaded(mapboxMap, it)
        }
    }

    private fun buildStyle() = Style.Builder().fromUrl(MY_STYLE)
        .apply {
            if (BuildConfig.ADMIN_MONITOR) {
                boundsSource?.let(::withSource)
                withLayer(FillLayer(BOUNDS_LAYER_ID, BOUNDS_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.fillColor(BOUNDS_COLOR)
                    )
                })
            }
        }
        .withImage(TARGET_IMAGE, checkNotNull(ContextCompat.getDrawable(this, R.drawable.target)))
        .withSource(targetSource)
        .withLayer(SymbolLayer(TARGET_LAYER_ID, TARGET_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(TARGET_IMAGE),
                PropertyFactory.iconAllowOverlap(true)
            )
        })
        .withSource(radiationSource)
        .withLayer(FillLayer(RADIATION_LAYER_ID, RADIATION_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.fillOpacity(RADIATION_OPACITY),
                PropertyFactory.fillColor(Expression.get(RadiationArea.COLOR_PROPERTY))
            )
        })
        .apply {
            if (BuildConfig.COMMAND_APP) {
                withImage(TEAM_IMAGE, checkNotNull(ContextCompat.getDrawable(this@MainActivity, R.drawable.team)))
                withSource(teamSource)
                withLayer(SymbolLayer(TEAM_LAYER_ID, TEAM_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage(TEAM_IMAGE),
                        PropertyFactory.iconAllowOverlap(true)
                    )
                })
            }

            if (BuildConfig.MOCK_ENABLED) {
                withImage(MOCK_IMAGE, checkNotNull(ContextCompat.getDrawable(this@MainActivity, R.drawable.team)))
                mockSource?.let(::withSource)
                withLayer(SymbolLayer(MOCK_LAYER_ID, MOCK_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage(MOCK_IMAGE),
                        PropertyFactory.iconAllowOverlap(true)
                    )
                })
            }
        }

    private fun onStyleLoaded(mapboxMap: MapboxMap, style: Style) {
        locationComponentWrangler?.apply {
            activate(mapboxMap, style)
            attemptEnable()
        }

        if (!BuildConfig.ADMIN_MONITOR) {
            vm.currentBounds.observe(this, Observer { bounds ->
                mapboxMap.apply {
                    setLatLngBoundsForCameraTarget(bounds)
                    setMinZoomPreference(0.0)
                    bounds?.let(::getCameraForLatLngBounds)?.let {
                        setMinZoomPreference(it.zoom)
                        cameraPosition = it
                    }
                }
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationComponentWrangler?.onRequestPermissionsResult(requestCode)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()

        if (isFinishing) offlineUpdater?.active = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private inner class LocationComponentWrangler {
        private lateinit var locationComponent: LocationComponent

        fun activate(mapboxMap: MapboxMap, style: Style) {
            locationComponent = mapboxMap.locationComponent.also {
                it.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this@MainActivity, style).build()
                )
            }
        }
        fun attemptEnable() {
            if (!enableIfPermitted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
        fun onRequestPermissionsResult(requestCode: Int) {
            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                enableIfPermitted()
            }
        }
        private fun enableIfPermitted(): Boolean {
            val hasPermission =  ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                locationComponent.isLocationComponentEnabled = true
                vm.lastLocation?.locationEngine = locationComponent.locationEngine
            }
            return hasPermission
        }
    }
}
