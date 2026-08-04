package com.danglingmind.convotrack

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

    // Pending ride-invite code from a deep link (convotrack://join/CODE or the
    // https App Link). Read by AppRoot; cleared once the join sheet consumes it.
    private val deepLinkCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkCode.value = inviteCodeFrom(intent)
        setContent {
            ConvoTrackTheme {
                AppRoot(
                    deepLinkCode = deepLinkCode.value,
                    onDeepLinkConsumed = { deepLinkCode.value = null },
                )
            }
        }
    }

    // App already running — pick up an invite that arrives while it's foregrounded.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteCodeFrom(intent)?.let { deepLinkCode.value = it }
    }

    private fun inviteCodeFrom(intent: Intent?): String? {
        val data = intent?.dataString ?: return null
        return extractInviteCode(data)
    }
}
