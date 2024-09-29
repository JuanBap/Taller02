package com.example.taller02

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

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
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.Date

class GoogleMapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityGoogleMapsBinding
    private var currentLocation: Location? = null
    val RADIUS_OF_EARTH_KM = 6371

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

        //Locations
        locationClient= LocationServices.getFusedLocationProviderClient(this)//es hija de activity entinces es base también
        locationRequest= createLocationRequest()
        locationCallback= createLocationCallback()

        //Solicitud del permiso
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val puj = LatLng(4.627119, -74.04229)
        mMap.addMarker(MarkerOptions().position(puj).title("Marker in PUJ"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(puj))
    }

    //-----------FUNCIONES REQUERIDAS PARA PUNTO 4-----------//     ---> VISTAS EN LA SESIÓN 6
    private fun createLocationRequest(): com.google.android.gms.location.LocationRequest {
        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)//encadenar llamados al mismo método
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
                        mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))
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
            .addLocationRequest(locationRequest)
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
                    //binding.altitude.text = "Device with no GPS!"
                }
            }
        }
    }

    private fun startLocationUpdates(){

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())//suscribirme a cambios, me falta la reacción a esos cambios (callback)
        }else{
            Toast.makeText(this, "NO PERMISSION", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause(){
        super.onPause()
        stopLocationUpdates()

    }


    private fun stopLocationUpdates(){
        locationClient.removeLocationUpdates(locationCallback)//si entra en pausa, cancelo suscripción de GPS
    }

    private fun persistLocation(){
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

    //-----------FUNCIONES PARA PUNTO 5,6,7,8 Y 9-----------//     ---> VISTAS EN LA SESIÓN 7

}