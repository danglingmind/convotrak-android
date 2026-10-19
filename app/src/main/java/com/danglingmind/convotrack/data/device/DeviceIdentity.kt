package com.danglingmind.convotrack.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Opaque per-device id sent as `X-Device-Id`, so the backend can give one free trial per device
 * rather than per account (iOS `DeviceIdentity`).
 *
 * Derived from `ANDROID_ID`, which since Android 8 is scoped to this app's signing key and user,
 * and survives uninstall/reinstall — a random UUID in app storage would not, and a fresh install
 * plus a fresh account would then mean a fresh trial. It is SHA-256'd with an app-specific salt so
 * the raw `ANDROID_ID` never leaves the device. Not used for ads or tracking (Play data safety:
 * "Device or other IDs", app functionality / fraud prevention).
 */
object DeviceIdentity {
    private const val SALT = "convotrack-trial-device-v1"

    @Volatile
    var id: String = ""
        private set

    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        id = if (!androidId.isNullOrBlank()) sha256("$SALT:$androidId") else fallback(context)
    }

    /** No ANDROID_ID (very rare): a stored random id still ties the trial to this install. */
    private fun fallback(context: Context): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
