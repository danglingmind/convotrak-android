package com.danglingmind.convotrack.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.danglingmind.convotrack.MainActivity
import com.danglingmind.convotrack.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Data-only FCM messages `{ type, rideId, rideName }`; the text comes from strings.xml. */
class ConvoFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val rideId = message.data["rideId"] ?: return
        val rideName = message.data["rideName"].orEmpty()
        val (title, body) = when (type) {
            "RIDE_STARTED" -> getString(R.string.push_ride_started_title) to getString(R.string.push_ride_started_body, rideName)
            "RIDE_ENDED" -> getString(R.string.push_ride_ended_title) to getString(R.string.push_ride_ended_body, rideName)
            else -> return
        }
        // Already looking at that ride: the screen says it all.
        if (Push.appInForeground() && Push.foregroundRideId == rideId) return
        if (!Push.hasPermission(this)) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notif_channel_updates_desc)
            },
        )
        val tap = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PUSH_TYPE, type)
            putExtra(MainActivity.EXTRA_PUSH_RIDE_ID, rideId)
        }
        val pending = PendingIntent.getActivity(
            this, "$type-$rideId".hashCode(), tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // Collapse per ride, like the server's collapse id.
        manager.notify("ride-$rideId", 0, notification)
    }

    private companion object {
        const val CHANNEL_ID = "ride_updates"
    }
}
