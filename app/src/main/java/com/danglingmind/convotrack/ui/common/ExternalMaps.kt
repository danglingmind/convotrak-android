package com.danglingmind.convotrack.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Starts turn-by-turn directions to a point in the rider's maps app.
 *
 * The web link (`google.com/maps/dir/?api=1&destination=…`) is not enough on its own: with no
 * origin, Google Maps opens on "Choose start location" and waits, so the button looked broken. The
 * `google.navigation:` scheme starts navigation from the current location straight away. Fallbacks,
 * in order: any app that handles `geo:` (other maps apps), then the web link in a browser.
 */
fun openDirections(context: Context, lat: Double, lng: Double, label: String? = null) {
    fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    val navigate = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
        .setPackage("com.google.android.apps.maps")
    if (tryStart(navigate)) return

    val name = label?.takeIf { it.isNotBlank() }?.let { "(${Uri.encode(it)})" }.orEmpty()
    if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng$name")))) return

    tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
}
