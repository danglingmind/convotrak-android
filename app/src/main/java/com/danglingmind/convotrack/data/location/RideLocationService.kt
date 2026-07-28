package com.danglingmind.convotrack.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.danglingmind.convotrack.MainActivity
import com.danglingmind.convotrack.R
import com.danglingmind.convotrack.di.ServiceLocator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground service that streams the rider's GPS during an active ride and emits it over the
 * socket (ride:location_update). Mirrors iOS LocationService.start(): high accuracy, ~5 m filter,
 * with a persistent notification so the user always knows tracking is on (Play policy).
 */
class RideLocationService : Service() {

    companion object {
        const val EXTRA_RIDE_ID = "rideId"
        private const val CHANNEL_ID = "ride_location"
        private const val NOTIF_ID = 42
    }

    private lateinit var fused: FusedLocationProviderClient
    private var rideId: String? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            ServiceLocator.location.update(loc)
            rideId?.let { id ->
                ServiceLocator.socket.emitLocation(
                    rideId = id,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0, // m/s (iOS parity)
                    heading = if (loc.hasBearing()) loc.bearing.toDouble() else 0.0,
                    battery = batteryLevel(),
                    signalStrength = "STRONG",
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rideId = intent?.getStringExtra(EXTRA_RIDE_ID) ?: rideId
        startAsForeground()
        requestUpdates()
        return START_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Live ride tracking", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows while your position is being shared during a ride." }
            manager.createNotificationChannel(channel)
        }
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ConvoTrack ride active")
            .setContentText("Sharing your live location with the pack")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else 0,
        )
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(5f)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission was revoked between the UI check and here — stop cleanly.
            stopSelf()
        }
    }

    private fun batteryLevel(): Double {
        val bm = getSystemService(BatteryManager::class.java) ?: return 1.0
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (pct in 0..100) pct / 100.0 else 1.0
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
