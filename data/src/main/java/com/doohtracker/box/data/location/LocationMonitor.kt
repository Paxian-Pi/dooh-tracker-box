package com.doohtracker.box.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

interface LocationMonitor {
    val currentLocation: Flow<Location?>
    val isLocationEnabled: Boolean
}

@Singleton
class LocationMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationMonitor {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override val isLocationEnabled: Boolean
        get() = LocationManagerCompat.isLocationEnabled(locationManager)

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override val currentLocation: Flow<Location?> = callbackFlow<Location?> {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000L).build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { trySend(it) }
            }
        }

        val legacyListener = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location) }
            @Suppress("DEPRECATION")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, fusedCallback, null)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 0f, legacyListener)
            }
            fusedLocationClient.lastLocation.addOnSuccessListener { it?.let { trySend(it) } }
        } catch (e: Exception) {
            Log.e("Location", "System location failed", e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(fusedCallback)
            locationManager.removeUpdates(legacyListener)
        }
    }
    .onStart {
        try {
            val last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            emit(last)
        } catch (e: Exception) {
            emit(null)
        }
    }
    .flatMapLatest { location ->
        if (location == null) {
            flow<Location?> {
                emit(null)
                val ipLocation = getIpLocationFallback()
                if (ipLocation != null) emit(ipLocation)
            }
        } else {
            flowOf(location)
        }
    }
    .distinctUntilChanged()

    private suspend fun getIpLocationFallback(): Location? = withContext(Dispatchers.IO) {
        Log.d("Location", "Attempting IP Fallback location...")
        tryIpApiCo() ?: tryIpWhoIs()
    }

    private fun tryIpApiCo(): Location? {
        return try {
            val url = URL("https://ipapi.co/json/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val lat = json.optDouble("latitude", Double.NaN)
                val lon = json.optDouble("longitude", Double.NaN)
                
                if (!lat.isNaN() && !lon.isNaN()) {
                    Location("IP-Geolocation").apply {
                        latitude = lat
                        longitude = lon
                        time = System.currentTimeMillis()
                    }
                } else {
                    Log.w("Location", "ipapi.co returned invalid coordinates")
                    null
                }
            } else {
                Log.w("Location", "ipapi.co failed with code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w("Location", "ipapi.co error: ${e.message}")
            null
        }
    }

    private fun tryIpWhoIs(): Location? {
        return try {
            val url = URL("https://ipwho.is/json/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    val lat = json.optDouble("latitude", Double.NaN)
                    val lon = json.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        Location("IP-Geolocation").apply {
                            latitude = lat
                            longitude = lon
                            time = System.currentTimeMillis()
                        }
                    } else {
                        Log.w("Location", "ipwho.is returned invalid coordinates")
                        null
                    }
                } else {
                    Log.w("Location", "ipwho.is returned success=false")
                    null
                }
            } else {
                Log.w("Location", "ipwho.is failed with code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w("Location", "ipwho.is error: ${e.message}")
            null
        }
    }
}
