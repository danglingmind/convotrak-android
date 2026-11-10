package com.danglingmind.convotrack

import com.danglingmind.convotrack.data.analytics.Analytics

import com.danglingmind.convotrack.ui.common.PlanGate
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.danglingmind.convotrack.ui.AppRoot
import com.danglingmind.convotrack.ui.scan.extractInviteCode
import com.danglingmind.convotrack.ui.theme.ConvoTrackTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the ride-status notifications; asks the app to open that ride's live navigation. */
        const val EXTRA_OPEN_RIDE_ID = "openRideId"
        /** Set by push notification taps: type (RIDE_STARTED / RIDE_ENDED) and the ride. */
        const val EXTRA_PUSH_TYPE = "pushType"
        const val EXTRA_PUSH_RIDE_ID = "pushRideId"
    }

    // Ride to jump into from a notification tap. Deliberately an extra, not intent data:
    // extractInviteCode() scans data for 6-character codes and would misread a ride URL.
    private val openRideId = mutableStateOf<String?>(null)

    private val pushTap = mutableStateOf<Pair<String, String>?>(null)

    // Pending ride-invite code from a deep link (convotrack://join/CODE or the
    // https App Link). Read by AppRoot; cleared once the join sheet consumes it.
    private val deepLinkCode = mutableStateOf<String?>(null)

    // Pending shared-route token (https://convotrack.in/route/<token> or convotrack://route/<token>).
    private val routeToken = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only, so pin the bar style. The default (auto) follows the system theme
        // and would draw dark icons on our dark background when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        routeToken.value = routeTokenFrom(intent)
        deepLinkCode.value = if (routeToken.value == null) inviteCodeFrom(intent) else null
        openRideId.value = intent?.getStringExtra(EXTRA_OPEN_RIDE_ID)
        if (savedInstanceState == null) pushTap.value = pushTapFrom(intent)
        // Debug only: `adb shell am start -n …/.MainActivity --es paywallState trial|lastday|expired|pro|free`
        // forces an access state for design review (iOS `-paywallState`).
        if (BuildConfig.DEBUG) {
            PlanGate.debugAccessOverride = intent?.getStringExtra("paywallState")
            // `--ez forceNudge true` shows the app-open nudge on this launch (iOS `-forceNudge`).
            PlanGate.debugForceNudge = intent?.getBooleanExtra("forceNudge", false) == true
        }
        setContent {
            ConvoTrackTheme {
                AppRoot(
                    deepLinkCode = deepLinkCode.value,
                    onDeepLinkConsumed = { deepLinkCode.value = null },
                    routeToken = routeToken.value,
                    onRouteTokenConsumed = { routeToken.value = null },
                    openRideId = openRideId.value,
                    onOpenRideConsumed = { openRideId.value = null },
                    pushTap = pushTap.value,
                    onPushTapConsumed = { pushTap.value = null },
                )
            }
        }
    }

    // App already running — pick up an invite that arrives while it's foregrounded.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val token = routeTokenFrom(intent)
        if (token != null) routeToken.value = token else inviteCodeFrom(intent)?.let { deepLinkCode.value = it }
        intent.getStringExtra(EXTRA_OPEN_RIDE_ID)?.let { openRideId.value = it }
        pushTapFrom(intent)?.let { pushTap.value = it }
    }

    private fun pushTapFrom(intent: Intent?): Pair<String, String>? {
        val type = intent?.getStringExtra(EXTRA_PUSH_TYPE) ?: return null
        val rideId = intent.getStringExtra(EXTRA_PUSH_RIDE_ID) ?: return null
        return type to rideId
    }

    /** Share tokens are 10 characters from the backend's no-lookalikes alphabet. Checked before the
     *  invite-code scan, which would otherwise read the first 6 characters of a route link. */
    private fun routeTokenFrom(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val isRoute = (uri.scheme == "convotrack" && uri.host == "route") ||
            (uri.scheme == "https" && uri.host == "convotrack.in" && uri.path?.startsWith("/route/") == true)
        if (!isRoute) return null
        val token = uri.lastPathSegment.orEmpty().uppercase().filter { it in "ABCDEFGHJKMNPQRSTUVWXYZ23456789" }
        if (token.length != 10) return null
        Analytics.capture(Analytics.Event.DeepLinkOpened, mapOf("kind" to "route"))
        return token
    }

    private fun inviteCodeFrom(intent: Intent?): String? {
        val data = intent?.dataString ?: return null
        return extractInviteCode(data)?.also {
            Analytics.capture(Analytics.Event.DeepLinkOpened, mapOf("kind" to "join"))
        }
    }
}
