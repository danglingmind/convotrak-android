package com.danglingmind.convotrack.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
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

        private const val MAX_USABLE_ACCURACY_M = 100f
        private const val MAX_FIX_AGE_MS = 5_000L
        /** Below this the rider is treated as stopped for broadcast-cadence purposes. Matches the
         *  band the speed readout flattens to a plain 0. */
        private const val STATIONARY_SPEED_KMH = 4.0
        private const val MOVING_BROADCAST_MS = 2_000L
        private const val STATIONARY_BROADCAST_MS = 10_000L
    }

    private lateinit var fused: FusedLocationProviderClient
    private var rideId: String? = null

    // Where the last broadcast went out from, so a rider who has genuinely stopped can be told
    // apart from one merely reading 0 km/h between two fixes.
    private var lastBroadcastAt = 0L
    private var lastBroadcastLocation: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (!isUsableForNavigation(loc)) return

            // The UI gets every usable fix — the speed readout, the ETA countdown and the
            // distance-to-turn all have to keep moving even when the rider is stationary.
            ServiceLocator.location.update(loc)

            // Broadcasts are throttled separately. Parked riders post on a slower cadence: now
            // that fixes arrive at a steady 1 Hz whether or not the bike is moving, a flat 2 s
            // interval would have a bike left outside a cafe posting an unchanged coordinate every
            // two seconds for as long as the ride is open. Nothing depends on that — presence runs
            // off its own 5 s ride:heartbeat, and a stopped rider's progress and leaderboard
            // position are by definition not changing.
            val id = rideId ?: return
            val now = android.os.SystemClock.elapsedRealtime()
            val speedKmh = if (loc.hasSpeed() && loc.speed >= 0) loc.speed * 3.6 else 0.0
            val isStationary = speedKmh < STATIONARY_SPEED_KMH &&
                lastBroadcastLocation?.let { it.distanceTo(loc) < 10f } == true
            val intervalMs = if (isStationary) STATIONARY_BROADCAST_MS else MOVING_BROADCAST_MS
            if (lastBroadcastAt != 0L && now - lastBroadcastAt < intervalMs) return
            lastBroadcastAt = now
            lastBroadcastLocation = loc

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

    /**
     * Whether a fix is good enough to steer navigation with.
     *
     * Everything downstream is debounced but nothing was ever filtered, so any fix the fused
     * provider produced went straight into a 40 m off-route decision and onto the map as the
     * rider's position. Two kinds are simply unusable:
     *
     * - Beyond [MAX_USABLE_ACCURACY_M] the fix cannot support a 40 m judgement at all. The bound
     *   is deliberately loose so a tunnel or a deep urban canyon still moves the puck rather than
     *   freezing it — this is aimed at the genuinely wild fix, not at merely mediocre ones.
     * - A fix older than [MAX_FIX_AGE_MS] is the cached one the provider replays the moment
     *   updates start. It can be minutes stale and hundreds of metres away, and handing it to the
     *   navigation screen flags the rider off-route and fires a reroute before they have moved.
     */
    private fun isUsableForNavigation(loc: Location): Boolean {
        if (!loc.hasAccuracy() || loc.accuracy <= 0f) return true   // no accuracy reported: trust it
        if (loc.accuracy > MAX_USABLE_ACCURACY_M) return false
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        return ageMs <= MAX_FIX_AGE_MS
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
        // NO minimum update distance. It reads like a harmless rate cap, but it gates DELIVERY,
        // not the GPS itself — the provider withholds the callback until the device has moved that
        // far. At any riding speed 5 m is crossed every fix, so the filter changed nothing while
        // moving; the moment the rider stopped it silenced the callback completely, and everything
        // downstream of a fix simply froze at its last value. The speed readout stuck at whatever
        // it read rolling up to the light, the ETA countdown stopped counting, and the distance to
        // the next turn held still — all until the rider pulled away and updates resumed.
        //
        // Full 1 Hz delivery is what a navigation app wants, and with HIGH_ACCURACY the receiver
        // is already running flat out either way, so this costs callbacks, not battery. Broadcast
        // volume is throttled in the callback instead, where "stopped" can be judged properly.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
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
