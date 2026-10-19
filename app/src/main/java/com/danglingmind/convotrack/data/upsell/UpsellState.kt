package com.danglingmind.convotrack.data.upsell

import android.content.Context
import android.content.SharedPreferences

/**
 * Upsell bookkeeping (iOS `UpsellState`): app opens, when the nudge and Home card were last shown
 * or dismissed. Plain SharedPreferences: nothing here is secret, and losing it only means a rider
 * may see an upsell a little sooner than the cadence would allow.
 */
class UpsellState(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("upsell", Context.MODE_PRIVATE)

    companion object {
        /** A return to the app only counts as a new open after this long in the background. */
        const val REOPEN_GAP_MS = 30 * 60_000L
        private const val DAY = 86_400_000L
    }

    private fun long(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    private fun put(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    val appOpenCount: Int get() = prefs.getInt("appOpenCount", 0)
    val firstOpenAt: Long? get() = long("firstOpenAt")
    val lastNudgeAt: Long? get() = long("lastNudgeAt")
    val nudgeDismissCount: Int get() = prefs.getInt("nudgeDismissCount", 0)
    val onboardingPaywallAt: Long? get() = long("onboardingPaywallAt")
    val rideCompletedAt: Long? get() = long("rideCompletedAt")

    /** Records a foreground. Returns true when it counts as a new app open. */
    fun recordForeground(now: Long = System.currentTimeMillis()): Boolean {
        val lastBackground = long("lastBackgroundAt")
        if (lastBackground != null && now - lastBackground < REOPEN_GAP_MS) return false
        prefs.edit().putInt("appOpenCount", appOpenCount + 1).apply()
        if (firstOpenAt == null) put("firstOpenAt", now)
        return true
    }

    fun recordBackground(now: Long = System.currentTimeMillis()) = put("lastBackgroundAt", now)
    fun recordNudgeShown(now: Long = System.currentTimeMillis()) = put("lastNudgeAt", now)
    fun recordNudgeDismissed() = prefs.edit().putInt("nudgeDismissCount", nudgeDismissCount + 1).apply()
    fun recordOnboardingPaywall(now: Long = System.currentTimeMillis()) = put("onboardingPaywallAt", now)

    /** The one-time paywall after first sign-in has been shown (or skipped for an existing rider). */
    var hasSeenOnboardingPaywall: Boolean
        get() = prefs.getBoolean("hasSeenOnboardingPaywall", false)
        set(value) = prefs.edit().putBoolean("hasSeenOnboardingPaywall", value).apply()
    fun recordRideCompleted(now: Long = System.currentTimeMillis()) = put("rideCompletedAt", now)

    fun isHomeCardSnoozed(now: Long = System.currentTimeMillis()): Boolean =
        long("homeCardSnoozedUntil")?.let { now < it } ?: false

    /** Dismissing during the trial snoozes a day; otherwise a week, a month after three no's. */
    fun snoozeHomeCard(inTrial: Boolean, now: Long = System.currentTimeMillis()) {
        val count = prefs.getInt("homeCardDismissCount", 0) + 1
        val days = if (inTrial) 1 else if (count >= 3) 30 else 7
        prefs.edit()
            .putInt("homeCardDismissCount", count)
            .putLong("homeCardSnoozedUntil", now + days * DAY)
            .apply()
    }

    fun inputs(access: com.danglingmind.convotrack.ui.common.Access, now: Long = System.currentTimeMillis()) =
        UpsellPolicy.Inputs(
            access = access,
            nowMillis = now,
            appOpenCount = appOpenCount,
            firstOpenAt = firstOpenAt,
            lastNudgeAt = lastNudgeAt,
            nudgeDismissCount = nudgeDismissCount,
            onboardingPaywallAt = onboardingPaywallAt,
            rideCompletedAt = rideCompletedAt,
        )
}
