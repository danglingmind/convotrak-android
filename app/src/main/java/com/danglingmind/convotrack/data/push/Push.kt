package com.danglingmind.convotrack.data.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.danglingmind.convotrack.BuildConfig
import com.danglingmind.convotrack.data.net.RegisterDeviceRequest
import com.danglingmind.convotrack.data.net.apiCall
import com.danglingmind.convotrack.di.ServiceLocator
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

/**
 * Push registration (docs/notifications-plan.md). Every Firebase call is guarded: without
 * google-services.json there is no default FirebaseApp, and all of this is a no-op.
 */
object Push {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val PREFS = "push"
    private const val KEY_ASKED = "permission_asked"
    private const val KEY_TOKEN = "token"

    /** The ride whose screen is on top (lobby/navigation/summary), or null. Set by AppRoot. */
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannels(context.applicationContext)
    }

    /** Created at start (not on first push) so they show in system settings before any push arrives. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                PushSpec.CHANNEL_SAFETY,
                context.getString(com.danglingmind.convotrack.R.string.notif_channel_safety),
                android.app.NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(com.danglingmind.convotrack.R.string.notif_channel_safety_desc) },
        )
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                PushSpec.CHANNEL_UPDATES,
                context.getString(com.danglingmind.convotrack.R.string.notif_channel_updates),
                android.app.NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(com.danglingmind.convotrack.R.string.notif_channel_updates_desc) },
        )
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    @Volatile var foregroundRideId: String? = null

    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun appInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** True once: when the runtime prompt is needed and has not been shown before. */
    fun shouldAskPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33 || hasPermission(context)) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        return true
    }

    /** Fetches the FCM token and uploads it. Needs permission, Firebase and a signed-in rider. */
    fun register(context: Context) {
        val app = context.applicationContext
        if (!isConfigured(app) || !hasPermission(app)) return
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                upload(app, token)
            }
        }
    }

    /** Token refresh from the messaging service. */
    fun onNewToken(context: Context, token: String) {
        val app = context.applicationContext
        if (ServiceLocator.auth.userState.value == null || !hasPermission(app)) return
        scope.launch { runCatching { upload(app, token) } }
    }

    private suspend fun upload(app: Context, token: String) {
        if (ServiceLocator.auth.userState.value == null) return
        apiCall {
            ServiceLocator.api.registerDevice(
                RegisterDeviceRequest(
                    token = token,
                    platform = "android",
                    locale = Locale.getDefault().toLanguageTag(),
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    /** Sign-out: delete the server row while the session is still valid, then drop the token. */
    suspend fun unregister() {
        runCatching {
            val app = appContext ?: return
            if (!isConfigured(app)) return
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_TOKEN, null) ?: FirebaseMessaging.getInstance().token.await()
            val ok = runCatching { apiCall { ServiceLocator.api.unregisterDevice(token) } }.isSuccess
            if (!ok) apiCall { ServiceLocator.api.unregisterDevice(token) }
            prefs.edit().remove(KEY_TOKEN).apply()
            FirebaseMessaging.getInstance().deleteToken().await()
        }
    }
}
