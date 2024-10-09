package com.example.taller02

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.taller02.databinding.ActivityGoogleMapsBinding
import com.example.taller02.model.MyLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.sql.Date

class GoogleMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityGoogleMapsBinding

    //Para OSM Bonuspack
    private lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null

    // Variables Punto 4
    private var currentLocation: Location? = null
    val RADIUS_OF_EARTH_KM = 6371

    // Variables Punto 5 - Sensores para luminosidad
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var darkSensor: Sensor? = null
    private lateinit var sensorEventListener: SensorEventListener

    //Geocoder
    private lateinit var geocoder: Geocoder

    // Permisos
    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings()
            }else{
                Toast.makeText(this, "NO PERMISSION GRANTED", Toast.LENGTH_LONG).show()
            }

        }
    )

    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if(it.resultCode == RESULT_OK){
                startLocationUpdates()
            }else{
                Toast.makeText(this, "The GPS is off!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    private lateinit var locationClient: FusedLocationProviderClient
    var locations = mutableListOf<JSONObject>()

    //Request y Callbacks
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGoogleMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // OSM Bonuspack
        // Inicializar el RoadManager
        roadManager = OSRMRoadManager(this, "ANDROID")

        // Política de seguridad para permitir llamados síncronos (solo para pruebas)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        //Locations
        locationClient= LocationServices.getFusedLocationProviderClient(this)
        locationRequest= createLocationRequest()
        locationCallback= createLocationCallback()

        //Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorEventListener = createSensorEventListener()

        //Geocoder
        geocoder = Geocoder(baseContext)

        //Solicitud del permiso
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        binding.traceRoutes.setOnClickListener {
            drawRouteJson()
        }

        binding.address.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val address = binding.address.text.toString()
                val location = findLocation(address)

                if (location != null) {
                    val address2 = this.findAddress(location)

                    // Agregar marcador en la ubicación buscada
                    drawMarker(location, address, R.drawable.location_pin)
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))

                    currentLocation?.let { currentLoc ->
                        // Asegurarse de que el marcador de currentLocation siempre esté presente
                        val currentLatLng = LatLng(currentLoc.latitude, currentLoc.longitude)

                        val distance = distance(currentLoc.latitude, currentLoc.longitude, location.latitude, location.longitude)
                        val formattedDistance = String.format("%.3f", distance)
                        Toast.makeText(this, "Distance to marker: $formattedDistance meters", Toast.LENGTH_SHORT).show()

                        // Castear localización actual a GeoPoint
                        val startGeoPoint = GeoPoint(currentLoc.latitude, currentLoc.longitude)
                        val finishGeoPoint = GeoPoint(location.latitude, location.longitude)

                        // Dibujar la ruta
                        drawRoute(startGeoPoint, finishGeoPoint)
                        drawMarker(location, address, R.drawable.location_pin)
                        drawMarker(currentLatLng, address, R.drawable.location_pin)
                    }
                }
            }
            return@setOnEditorActionListener true
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun drawRouteJson() {
        val filename = "locations.json"
        val file = File(baseContext.getExternalFilesDir(null), filename)
        if (!file.exists()) {
            Toast.makeText(this, "No locations found", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonString = BufferedReader(FileReader(file)).use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val routePoints = ArrayList<GeoPoint>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val latitude = jsonObject.getDouble("latitude")
            val longitude = jsonObject.getDouble("longitude")
            routePoints.add(GeoPoint(latitude, longitude))
        }

        if (routePoints.size < 2) {
            Toast.makeText(this, "Not enough points to draw a route", Toast.LENGTH_SHORT).show()
            return
        }

        val road = roadManager.getRoad(routePoints)
        if (mMap != null) {
            if (roadOverlay != null) {
                mMap.clear() // Clear all overlays
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.outlinePaint.color = Color.RED
            roadOverlay!!.outlinePaint.strokeWidth = 10F

            val polylineOptions = PolylineOptions()
            for (point in roadOverlay!!.points) {
                polylineOptions.add(LatLng(point.latitude, point.longitude))
            }
            polylineOptions.color(Color.RED)
            polylineOptions.width(10F)
            mMap.addPolyline(polylineOptions)

            // Add markers
            if (roadOverlay!!.points.isNotEmpty()) {
                // Origin marker
                val origin = roadOverlay!!.points.first()
                drawMarker(LatLng(origin.latitude, origin.longitude), "Origin", R.drawable.location_pin)


                // Destination marker
                val destination = roadOverlay!!.points.last()
                val destinationLatLng = LatLng(destination.latitude, destination.longitude)
                drawMarker(LatLng(destination.latitude, destination.longitude), "Destination", R.drawable.location_pin3)
                mMap.moveCamera(CameraUpdateFactory.newLatLng(destinationLatLng))
                mMap.moveCamera(CameraUpdateFactory.zoomTo(15f))
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true

        mMap.setOnMapLongClickListener {
            val address = this.findAddress(it)
            drawMarker(it, address, R.drawable.location_pin)
            currentLocation?.let { location ->
                val distance = distance(location.latitude, location.longitude, it.latitude, it.longitude)
                val formattedDistance = String.format("%.3f", distance)
                Toast.makeText(this, "Distance to marker: $formattedDistance meters", Toast.LENGTH_SHORT).show()

                val startGeoPoint = GeoPoint(location.latitude, location.longitude)
                val finishGeoPoint = GeoPoint(it.latitude, it.longitude)
                drawRoute(startGeoPoint, finishGeoPoint)
                drawMarker(it, address, R.drawable.location_pin)
                val currentLatLng = LatLng(location.latitude, location.longitude)
                drawMarker(currentLatLng, address, R.drawable.location_pin)
                //Mueve la camara al punto nuevo creado
                mMap.moveCamera(CameraUpdateFactory.newLatLng(it))
            }
        }
    }

    private fun createLocationRequest(): com.google.android.gms.location.LocationRequest {
        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(3000).build()
        return request
    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.lastLocation
                if (location != null) {
                    if (currentLocation == null) {
                        currentLocation = location
                        // Update the map with the current location
                        val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location: ${findAddress(currentLatLng)} "))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        if (distance(currentLocation!!.latitude, currentLocation!!.longitude, location.latitude, location.longitude) > 0.03) {
                            currentLocation = location
                            persistLocation()
                        }
                    }
                }
            }
        }
        return callback
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsRespose ->
            startLocationUpdates()
        }
        task.addOnFailureListener { excepcion ->
            if (excepcion is ResolvableApiException) {
                try {
                    var isr: IntentSenderRequest =
                        IntentSenderRequest.Builder(excepcion.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun persistLocation() {
        val myLocation = MyLocation(Date(System.currentTimeMillis()), currentLocation!!.latitude, currentLocation!!.longitude)
        locations.add(myLocation.toJSON())
        val filename= "locations.json"
        val file = File(baseContext.getExternalFilesDir(null), filename)
        val output = BufferedWriter(FileWriter(file))
        output.write(locations.toString())
        output.close()
        Log.i("LOCATION", "File modified at path" + file)
    }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat1 - lat2)
        val dLon = Math.toRadians(lon1 - lon2)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val result = RADIUS_OF_EARTH_KM * c
        return Math.round(result * 1000.0) / 1000.0
    }

    private fun createSensorEventListener(): SensorEventListener {
        val listener : SensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if(this@GoogleMapsActivity::mMap.isInitialized) {
                    if (lightSensor != null) {
                        if (event != null) {
                            if (event.values[0] < 5000) {
                                mMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                        baseContext,
                                        R.raw.map_dark
                                    )
                                )
                            } else {
                                mMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                        baseContext,
                                        R.raw.map_light
                                    )
                                )
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }
        return listener
    }

    fun drawMarker(location : LatLng, description : String?, icon: Int) {
        val addressMarker = mMap.addMarker(MarkerOptions().position(location).icon(bitmapDescriptorFromVector(this, icon)))!!
        if(description != null) {
            addressMarker.title = description
        }
    }

    fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable : Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun findAddress(location : LatLng): String? {
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 2)
        if(addresses != null && addresses.isNotEmpty()) {
            val addr = addresses[0]
            val locname = addr.getAddressLine(0)
            return locname
        }
        return null
    }

    fun findLocation(address : String): LatLng? {
        val addresses = geocoder.getFromLocationName(address, 2)
        if(addresses != null && addresses.isNotEmpty()) {
            val addr = addresses[0]
            val location = LatLng(addr.latitude, addr.longitude)
            return location
        }
        return null
    }

    fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        val routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        val road = roadManager.getRoad(routePoints)
        Log.i("MapsApp", "Route length: ${road.mLength} km")
        Log.i("MapsApp", "Duration: ${road.mDuration / 60} min")
        if (mMap != null) {
            if (roadOverlay != null) {
                mMap.clear() // Clear all overlays
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.outlinePaint.color = Color.RED
            roadOverlay!!.outlinePaint.strokeWidth = 10F

            val polylineOptions = PolylineOptions()
            for (point in roadOverlay!!.points) {
                polylineOptions.add(LatLng(point.latitude, point.longitude))
            }
            polylineOptions.color(Color.RED)
            polylineOptions.width(10F)
            mMap.addPolyline(polylineOptions)
        }
    }
}