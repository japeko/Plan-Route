package com.planroute.app.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint

private const val FixTimeoutMillis = 15_000L
private const val TrackingMinIntervalMillis = 2_000L
private const val TrackingMinDistanceMeters = 5f

/** Wraps the device's own location sensors — no network service involved. */
object LocationRepository {
    suspend fun currentLocation(context: Context): GeoPoint? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(FixTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(GeoPoint(location.latitude, location.longitude)))
                        }
                    }
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                @Suppress("DEPRECATION") // requestSingleUpdate is the only single-fix API back to minSdk 26
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }

    /** Continuous fixes for as long as it's collected — used while navigating to follow the vehicle and derive its speed. */
    fun trackLocation(context: Context): Flow<Location> = callbackFlow {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = when {
            locationManager == null -> null
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (locationManager == null || provider == null) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
        }
        locationManager.requestLocationUpdates(provider, TrackingMinIntervalMillis, TrackingMinDistanceMeters, listener, Looper.getMainLooper())
        awaitClose { locationManager.removeUpdates(listener) }
    }
}
