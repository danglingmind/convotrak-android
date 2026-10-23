package com.danglingmind.convotrack.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One fix for planning a ride — where "Your location" is and where the map opens. Separate from
 * [LocationController], which only carries fixes while a ride's foreground service is running.
 * Returns null without the permission or a fix; the planner then asks for a start instead.
 */
object CurrentLocation {

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")   // checked by isGranted
    suspend fun get(context: Context): LatLng? {
        if (!isGranted(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // The last known fix is instant and good enough to start a ride from. Without one (a cold
        // provider, an emulator) ask for a fresh fix; balanced accuracy can come back empty there.
        val last = suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
        if (last != null) return last
        return suspendCancellableCoroutine { cont ->
            val token = CancellationTokenSource()
            cont.invokeOnCancellation { token.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
