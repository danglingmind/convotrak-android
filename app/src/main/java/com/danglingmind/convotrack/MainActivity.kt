package com.danglingmind.convotrack

import com.danglingmind.convotrack.ui.common.PlanGate
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.danglingmind.convotrack.ui.AppRoot
import com.danglingmind.convotrack.ui.scan.extractInviteCode
import com.danglingmind.convotrack.ui.theme.ConvoTrackTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the ride-status notifications; asks the app to open that ride's live navigation. */
        const val EXTRA_OPEN_RIDE_ID = "openRideId"
    }

    // Ride to jump into from a notification tap. Deliberately an extra, not intent data:
    // extractInviteCode() scans data for 6-character codes and would misread a ride URL.
    private val openRideId = mutableStateOf<String?>(null)

    // Pending ride-invite code from a deep link (convotrack://join/CODE or the
    // https App Link). Read by AppRoot; cleared once the join sheet consumes it.
    private val deepLinkCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkCode.value = inviteCodeFrom(intent)
        openRideId.value = intent?.getStringExtra(EXTRA_OPEN_RIDE_ID)
        // Debug only: `adb shell am start -n …/.MainActivity --es paywallState trial|lastday|expired|pro|free`
        // forces an access state for design review (iOS `-paywallState`).
        if (BuildConfig.DEBUG) PlanGate.debugAccessOverride = intent?.getStringExtra("paywallState")
        setContent {
            ConvoTrackTheme {
                AppRoot(
                    deepLinkCode = deepLinkCode.value,
                    onDeepLinkConsumed = { deepLinkCode.value = null },
                    openRideId = openRideId.value,
                    onOpenRideConsumed = { openRideId.value = null },
                )
            }
        }
    }

    // App already running — pick up an invite that arrives while it's foregrounded.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteCodeFrom(intent)?.let { deepLinkCode.value = it }
        intent.getStringExtra(EXTRA_OPEN_RIDE_ID)?.let { openRideId.value = it }
    }

    private fun inviteCodeFrom(intent: Intent?): String? {
        val data = intent?.dataString ?: return null
        return extractInviteCode(data)
    }
}
