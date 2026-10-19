package com.danglingmind.convotrack.data.location

import android.app.Notification
import android.app.PendingIntent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        private const val EMERGENCY_CHANNEL_ID = "ride_emergency"
        private const val EMERGENCY_NOTIF_ID = 43
        /** Routine status refreshes (countdown, gap) are coalesced to one per this interval. */
        private const val STATUS_THROTTLE_MS = 5_000L

        private const val MAX_USABLE_ACCURACY_M = 100f
        private const val MAX_FIX_AGE_MS = 5_000L
        /** Below this the rider is treated as stopped for broadcast-cadence purposes. Matches the
         *  band the speed readout flattens to a plain 0. */
        /** Speed at which the rider counts as moving for broadcast cadence. Below the display
         *  band on purpose — that band steadies a readout, it does not decide how often the pack
         *  hears from someone crawling through traffic. */
        private const val MOVING_SPEED_KMH = 2.0
        private const val MOVING_BROADCAST_MS = 2_000L
        private const val STATIONARY_BROADCAST_MS = 10_000L
    }

    private lateinit var fused: FusedLocationProviderClient
    private var rideId: String? = null

    // Live-status notification state. Everything below runs on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusJob: Job? = null
    private var pendingPost: Job? = null
    private var latestStatus: RideStatus? = null
    private var lastPostedAt = 0L
    private var lastPostedKey: String? = null
    private var lastEmergencyName: String? = null

    // Where the last broadcast went out from, so a rider who has genuinely stopped can be told
    // apart from one merely reading 0 km/h between two fixes.
    private var lastBroadcastAt = 0L

    /** Anchored on the last FAST broadcast only, so displacement accumulates across slow ticks and
     *  sustained movement escapes the slow cadence on its own. */
    private var lastFastBroadcastLocation: Location? = null

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
            // Both halves of this test used to latch a moving rider onto the slow cadence. The
            // speed was compared against the DISPLAY band, so anything under 4 km/h read as parked;
            // and the displacement was measured from the last broadcast of any kind, re-anchored on
            // every slow tick, so a ~3 km/h crawl covering ~8 m per 10 s interval never accumulated
            // past the 10 m bar. Everyone else's pin, leaderboard distance and edge indicator for
            // that rider then lagged by up to 10 s, in exactly the dense traffic where the pack
            // needs them live.
            val movingBySpeed = loc.hasSpeed() && loc.speed >= 0 && loc.speed * 3.6 >= MOVING_SPEED_KMH
            val isStationary = !movingBySpeed &&
                lastFastBroadcastLocation?.let { it.distanceTo(loc) < 10f } == true
            val intervalMs = if (isStationary) STATIONARY_BROADCAST_MS else MOVING_BROADCAST_MS
            if (lastBroadcastAt != 0L && now - lastBroadcastAt < intervalMs) return
            lastBroadcastAt = now
            if (!isStationary) lastFastBroadcastLocation = loc

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
        observeStatus()
        requestUpdates()
        return START_STICKY
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live ride tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Next turn, arrival time and convoy status while a ride is live."
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(EMERGENCY_CHANNEL_ID, "Ride emergencies", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a rider in your convoy reports an emergency."
            },
        )
    }

    private fun startAsForeground() {
        ensureChannels()
        // Always renders the latest status: this also runs on every re-start of the service, and
        // must not flash the generic text over a live one.
        val notification = buildOngoing(latestStatus ?: RideStatusNotifier.status.value)
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else 0,
        )
    }

    // MARK: - Live status

    /** Tapping any of our notifications opens the app straight into this ride's navigation. */
    private fun openRidePendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_RIDE_ID, rideId ?: latestStatus?.rideId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun observeStatus() {
        if (statusJob != null) return
        statusJob = scope.launch {
            RideStatusNotifier.status.collect { status ->
                latestStatus = status
                onStatus(status)
            }
        }
    }

    /** Immediate on a step change or emergency start/end; otherwise at most once per throttle window. */
    private fun onStatus(status: RideStatus?) {
        handleEmergencyTransition(status?.emergencyName)
        val key = "${status?.maneuverKey}|${status?.instruction}|${status?.emergencyName}|${status == null}"
        val now = android.os.SystemClock.elapsedRealtime()
        val urgent = key != lastPostedKey
        val wait = STATUS_THROTTLE_MS - (now - lastPostedAt)
        if (urgent || wait <= 0) {
            pendingPost?.cancel(); pendingPost = null
            postOngoing(status, key)
        } else if (pendingPost == null) {
            pendingPost = scope.launch {
                delay(wait)
                pendingPost = null
                postOngoing(latestStatus, null)
            }
        }
    }

    private fun postOngoing(status: RideStatus?, keyOverride: String?) {
        lastPostedAt = android.os.SystemClock.elapsedRealtime()
        lastPostedKey = keyOverride
            ?: "${status?.maneuverKey}|${status?.instruction}|${status?.emergencyName}|${status == null}"
        try {
            // Same id as the foreground notification: this updates it in place, it never restarts
            // the service or the foreground state. Silently dropped if notifications are blocked.
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildOngoing(status))
        } catch (_: Exception) {
        }
    }

    private fun handleEmergencyTransition(name: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        try {
            if (name != null && lastEmergencyName == null) {
                // The reporter already knows; the heads-up is for the rest of the pack.
                if (name != "You") manager.notify(EMERGENCY_NOTIF_ID, buildEmergency(name))
            } else if (name == null && lastEmergencyName != null) {
                manager.cancel(EMERGENCY_NOTIF_ID)
            }
        } catch (_: Exception) {
        }
        lastEmergencyName = name
    }

    private fun buildEmergency(name: String): Notification {
        val publicVersion = Notification.Builder(this, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Emergency on your ride")
            .build()
        return Notification.Builder(this, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Emergency - $name needs help")
            .setContentText("Open ConvoTrack for their location")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openRidePendingIntent(2))
            .build()
    }

    private fun buildOngoing(status: RideStatus?): Notification {
        // Lock-screen version carries nothing about where the rider is or who needs help.
        val publicVersion = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("ConvoTrack ride active")
            .build()
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openRidePendingIntent(1))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (status == null) {
            return b.setContentTitle("ConvoTrack ride active")
                .setContentText("Sharing your live location with the pack")
                .build()
        }

        val turn = listOf(status.turnDistanceText, status.instruction).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
        val emergency = status.emergencyName?.let { if (it == "You") "Emergency raised" else "Emergency - $it needs help" }
        val arrive = if (status.arrivalText.isBlank()) "" else "Arrive ${status.arrivalText} \u00B7 ${status.timeLeftText}"
        b.setContentTitle(emergency ?: turn.ifBlank { "Ride in progress" })
        b.setContentText(if (emergency != null && turn.isNotBlank()) turn else arrive)
        b.setSubText(status.destinationName)
        b.setLargeIcon(ManeuverGlyph.bitmap(status.maneuverKey))

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update: progress bar along the route + arrival time as the chip text.
            val text = listOf(if (emergency != null && turn.isNotBlank()) turn else arrive, status.convoyGapText)
                .filter { it.isNotBlank() }.joinToString(" \u00B7 ")
            b.setContentText(text)
            b.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
                    .setProgress((status.progress.coerceIn(0f, 1f) * 100).toInt()),
            )
            if (status.arrivalShort.isNotBlank()) b.setShortCriticalText(status.arrivalShort)
            // setRequestPromotedOngoing(true) isn't in compileSdk 36.0's android.jar (36.1+); the
            // extra it sets is Notification.EXTRA_REQUEST_PROMOTED_ONGOING, so set it directly.
            // Promotion is a request: the OS only honours it when the user allows it for this app.
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.canPostPromotedNotifications()) {
                b.addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            }
        } else {
            val expanded = listOf(arrive, status.convoyGapText).filter { it.isNotBlank() }.joinToString("\n")
            if (expanded.isNotBlank()) b.setStyle(Notification.BigTextStyle().bigText(expanded))
        }
        return b.build()
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
        scope.cancel()
        RideStatusNotifier.clear()
        try { getSystemService(NotificationManager::class.java).cancel(EMERGENCY_NOTIF_ID) } catch (_: Exception) {}
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
