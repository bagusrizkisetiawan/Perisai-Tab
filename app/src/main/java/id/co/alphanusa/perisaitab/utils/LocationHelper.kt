package id.co.alphanusa.perisaitab.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices

interface ILocationHelper {
    fun getLastLocation(onResult: (Location?) -> Unit)
    fun startLocationUpdates(callback: (Location) -> Unit)
    fun stopLocationUpdates()
}

class HuaweiLocationHelper(context: Context) : ILocationHelper {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun getLastLocation(onResult: (Location?) -> Unit) {
        client.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onResult(location)
                } else {
                    requestSingleUpdate(onResult)
                }
            }
            .addOnFailureListener {
                requestSingleUpdate(onResult)
            }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(onLocation: (Location) -> Unit) {
        callback?.let { client.removeLocationUpdates(it) }

        val request = LocationRequest().apply {
            interval = 2000L
            fastestInterval = 1000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult?) {
                result?.lastLocation?.let { onLocation(it) }
            }
        }

        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleUpdate(onResult: (Location?) -> Unit) {
        callback?.let { client.removeLocationUpdates(it) }

        val request = LocationRequest().apply {
            interval = 5_000L
            fastestInterval = 2_000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 0f
            numUpdates = 1
        }

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult?) {
                result?.lastLocation?.let { location ->
                    onResult(location)
                    stopLocationUpdates()
                }
            }
        }

        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    override fun stopLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}

class GoogleLocationHelper(context: Context) : ILocationHelper {

    private val client =
        com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(context)

    private var callback: com.google.android.gms.location.LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun getLastLocation(onResult: (Location?) -> Unit) {
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onResult(location)
            } else {
                requestSingleUpdate(onResult)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(onLocation: (Location) -> Unit) {
        callback?.let { client.removeLocationUpdates(it) }

        val request = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).setMinUpdateIntervalMillis(1000L).build()

        callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }

        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleUpdate(onResult: (Location?) -> Unit) {
        val request = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        ).setMinUpdateIntervalMillis(2000L)
            .setMaxUpdates(1)
            .build()

        callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                onResult(result.lastLocation)
                stopLocationUpdates()
            }
        }

        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    override fun stopLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}




class NativeLocationHelper(context: Context) : ILocationHelper {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun getLastLocation(onResult: (Location?) -> Unit) {
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider)
            if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                bestLocation = l
            }
        }
        onResult(bestLocation)
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates(onLocation: (Location) -> Unit) {
        // Hapus update sebelumnya jika ada
        stopLocationUpdates()

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location)
            }
            // Overrides kosong ini wajib untuk Android lawas (di bawah API 30)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L, // Interval 2 detik
                0f,    // Jarak minimal 0 meter
                locationListener!!
            )
        }

        if (isNetworkEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                0f,
                locationListener!!
            )
        }
    }

    override fun stopLocationUpdates() {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
    }
}