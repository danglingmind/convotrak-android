package com.danglingmind.convotrack.data.analytics

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.danglingmind.convotrack.BuildConfig
import com.danglingmind.convotrack.ui.common.Access
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Product analytics (PostHog), iOS `Analytics` parity. Interface-level events only: screens,
 * sharing, permissions, sign-in. Ride lifecycle, safety and revenue events are captured by the
 * backend, the only place that knows they really happened. Paywall events go through
 * [PaywallAnalytics] → `POST /events` and the backend forwards them, so they are deliberately not
 * sent from here too.
 *
 * Talks to PostHog's `/batch/` endpoint directly rather than through their SDK: the surface needed
 * is small and the iOS app has no package dependencies either.
 *
 * Privacy: the rider is identified by their Clerk user id and nothing else. Never put coordinates,
 * place names, invite codes, names, emails or phone numbers in a property. The taxonomy is
 * `docs/analytics.md` in the iOS repo; keep it, [Event], and the iOS `Analytics.Event` in step.
 *
 * Off when `POSTHOG_API_KEY` (local.properties) is empty, in debug builds (unless [debugEnabled]
 * is flipped), and when the rider has opted out ([isOptedOut]).
 */
object Analytics {
    /** The wire names. Past tense, snake_case, one name per meaning. */
    enum class Event(val wire: String) {
        // App lifecycle
        AppOpened("app_opened"),
        // Auth
        SignInStarted("sign_in_started"),
        SignInSucceeded("sign_in_succeeded"),
        SignInFailed("sign_in_failed"),
        SignedOut("signed_out"),
        // Navigation
        ScreenViewed("screen_viewed"),
        DeepLinkOpened("deep_link_opened"),
        // Rides (interface side; the server records the outcome)
        CreateRideFailed("create_ride_failed"),
        JoinSheetOpened("join_sheet_opened"),
        JoinFailed("join_failed"),
        QrScanOpened("qr_scan_opened"),
        InviteShareOpened("invite_share_opened"),
        RideSummaryViewed("ride_summary_viewed"),
        RideSummaryShared("ride_summary_shared"),
        // Permissions
        PermissionResult("permission_result"),
        VoiceMuteToggled("voice_mute_toggled"),
    }

    /** Debug builds send nothing unless this is flipped, so development never pollutes the data. */
    @Volatile var debugEnabled = false

    private const val PREFS = "analytics"
    private const val KEY_OPT_OUT = "opt_out"
    private const val KEY_ANON_ID = "anon_id"
    private const val MAX_BATCH = 50
    private const val MAX_QUEUE = 200
    private const val FLUSH_DELAY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val flushMutex = Mutex()
    private val queue = ArrayDeque<JSONObject>()
    private var flushJob: Job? = null

    private lateinit var app: Application
    private var started = false
    private var currentUserId: String? = null
    private var accessName: String? = null

    var isOptedOut: Boolean
        get() = prefs().getBoolean(KEY_OPT_OUT, false)
        set(value) {
            prefs().edit().putBoolean(KEY_OPT_OUT, value).apply()
            if (value) synchronized(lock) { queue.clear() }
        }

    /** Call once from `Application.onCreate`. */
    fun start(application: Application) {
        if (started) return
        started = true
        app = application
        var first = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                capture(Event.AppOpened, mapOf("cold" to first))
                first = false
            }

            override fun onStop(owner: LifecycleOwner) {
                // The process may be frozen right after this, so send now rather than in 5 s.
                scope.launch { flush() }
            }
        })
    }

    fun capture(event: Event, props: Map<String, Any?> = emptyMap()) = enqueue(event.wire, props)

    fun screen(name: String) = capture(Event.ScreenViewed, mapOf("screen" to name))

    /** Tie this device's earlier anonymous events to the signed-in rider. Safe to call repeatedly. */
    fun identify(userId: String) {
        if (!started || userId == currentUserId) return
        val anon = anonId()
        currentUserId = userId
        enqueue("\$identify", mapOf("\$anon_distinct_id" to anon))
    }

    /**
     * Sign-out: stop attributing to the old rider and start a fresh anonymous identity.
     * [recordSignOut] is false for account deletion, which the server records itself.
     */
    fun reset(recordSignOut: Boolean = true) {
        if (!started) return
        if (recordSignOut) capture(Event.SignedOut)
        scope.launch { flush() }
        currentUserId = null
        prefs().edit().putString(KEY_ANON_ID, UUID.randomUUID().toString()).apply()
    }

    /** The rider's plan tier, attached to every event so any chart can be split by it. */
    fun setAccess(access: Access) {
        accessName = when (access) {
            Access.Pro -> "pro"
            is Access.Trial -> "trial"
            Access.TrialExpired -> "trialExpired"
            Access.Free -> "free"
        }
    }

    // region internals

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun anonId(): String =
        prefs().getString(KEY_ANON_ID, null) ?: UUID.randomUUID().toString().also {
            prefs().edit().putString(KEY_ANON_ID, it).apply()
        }

    private val enabled: Boolean
        get() = started && BuildConfig.POSTHOG_API_KEY.isNotEmpty() && !isOptedOut &&
            (!BuildConfig.DEBUG || debugEnabled)

    private fun enqueue(name: String, props: Map<String, Any?>) {
        if (!enabled) return
        val properties = JSONObject().apply {
            put("platform", "android")
            put("\$app_version", BuildConfig.VERSION_NAME)
            put("\$app_build", BuildConfig.VERSION_CODE)
            put("\$os", "Android")
            put("\$os_version", Build.VERSION.RELEASE)
            put("\$lib", "convotrack-android")
            put("\$locale", Locale.getDefault().toLanguageTag())
            put("source", "client")
            accessName?.let { put("access", it) }
            props.forEach { (k, v) -> if (v != null) put(k, v) }
        }
        val event = JSONObject().apply {
            put("event", name)
            put("distinct_id", currentUserId ?: anonId())
            put("properties", properties)
            put("timestamp", Instant.now().toString())
        }
        synchronized(lock) {
            queue.addLast(event)
            // Bounded so an offline rider can't grow it; the oldest go first.
            while (queue.size > MAX_QUEUE) queue.removeFirst()
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private suspend fun flush() {
        if (!enabled) return
        flushMutex.withLock {
            while (true) {
                val batch = synchronized(lock) {
                    List(minOf(MAX_BATCH, queue.size)) { queue.removeFirst() }
                }
                if (batch.isEmpty()) return
                if (!post(batch)) {
                    // Keep it for the next flush (bounded) and stop rather than spin against a dead host.
                    synchronized(lock) {
                        batch.asReversed().forEach { queue.addFirst(it) }
                        while (queue.size > MAX_QUEUE) queue.removeFirst()
                    }
                    scheduleFlush()
                    return
                }
            }
        }
    }

    private suspend fun post(batch: List<JSONObject>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("api_key", BuildConfig.POSTHOG_API_KEY)
                put("batch", JSONArray(batch))
            }.toString().toByteArray()
            val conn = (URL(BuildConfig.POSTHOG_HOST.trimEnd('/') + "/batch/").openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    // endregion
}
