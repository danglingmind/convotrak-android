package com.danglingmind.convotrack.ui.common

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danglingmind.convotrack.ui.theme.CT
import com.danglingmind.convotrack.ui.theme.CTType

/**
 * Prominent disclosure, shown before the system location prompt.
 *
 * Play requires this whenever location leaves the device, and the system prompt cannot stand in
 * for it: that dialog offers a bare "allow this app to access your location", which is true of a
 * weather app and of this one alike. What it never says is the part that matters here — the
 * position is broadcast live to the other riders in the convoy, it goes to our server on the way,
 * and it keeps going while the phone is in a pocket or the screen is off.
 *
 * Three things have to be present for the disclosure to count, and each maps to a paragraph
 * below: what is collected, who it goes to, and when it stops. Declining has to be a real option,
 * which is why the dismiss button is not styled as an afterthought.
 *
 * Only shown when the permission is not already held, so a rider who granted it once does not see
 * this again on every ride.
 */
@Composable
fun LocationDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = CT.surfaceContainer,
        title = { Text("Sharing your location", style = CTType.headlineMd, color = CT.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To ride in a convoy, ConvoTrack collects your precise location and shares it " +
                        "live with the other riders in this ride. It also goes to our servers, " +
                        "which is what draws the route, the leaderboard and everyone's map pins.",
                    style = CTType.bodyMd,
                    color = CT.onSurfaceVariant,
                )
                Text(
                    "This continues while the app is in the background so the pack doesn't lose " +
                        "you at a junction. A notification stays in your status bar for as long as " +
                        "it is running.",
                    style = CTType.bodyMd,
                    color = CT.onSurfaceVariant,
                )
                Text(
                    "It stops the moment the ride ends or you leave it. You can ride without this, " +
                        "but the others won't see where you are.",
                    style = CTType.bodyMd,
                    color = CT.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("SHARE MY LOCATION", style = CTType.labelCaps, color = CT.primaryFixed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("NOT NOW", style = CTType.labelCaps, color = CT.onSurfaceVariant)
            }
        },
    )
}

/**
 * What the rider sees after refusing, or after the system prompt comes back denied.
 *
 * The refusal branch previously did nothing at all: the screen loaded, the map never followed the
 * rider, and nothing on it explained why. Saying plainly what has been lost — and offering the one
 * route back, since a second system prompt will not appear once it has been denied twice — is the
 * difference between a considered degraded state and an app that looks broken.
 */
@Composable
fun LocationDeniedDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CT.surfaceContainer,
        title = { Text("You're riding dark", style = CTType.headlineMd, color = CT.onSurface) },
        text = {
            Text(
                "Without location access the other riders can't see you on the map, and turn-by-turn " +
                    "guidance won't follow you. Everything else in the ride still works — you'll see " +
                    "them, they just won't see you.",
                style = CTType.bodyMd,
                color = CT.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
                onDismiss()
            }) {
                Text("OPEN SETTINGS", style = CTType.labelCaps, color = CT.primaryFixed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CARRY ON", style = CTType.labelCaps, color = CT.onSurfaceVariant)
            }
        },
    )
}
