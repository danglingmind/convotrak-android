package com.danglingmind.convotrack.data.upsell

import com.danglingmind.convotrack.ui.common.Access
import java.time.Instant
import java.time.ZoneId

/**
 * When the app-open nudge may appear. Pure — every input is passed in — and kept rule-for-rule
 * identical to iOS `UpsellPolicy` (see the CLAUDE.md "values that must match" table).
 */
object UpsellPolicy {
    data class Inputs(
        val access: Access,
        val nowMillis: Long,
        val appOpenCount: Int,
        val firstOpenAt: Long?,
        val lastNudgeAt: Long?,
        val nudgeDismissCount: Int,
        val onboardingPaywallAt: Long?,
        val rideCompletedAt: Long?,
    )

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    fun shouldShowAppOpenNudge(i: Inputs, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        // Never right on the heels of the onboarding paywall.
        if (i.onboardingPaywallAt != null && i.nowMillis - i.onboardingPaywallAt < DAY) return false

        return when (val a = i.access) {
            Access.Pro -> false

            is Access.Trial -> {
                val left = a.endsAtMillis - i.nowMillis
                when {
                    left > 2 * DAY -> false                          // day 1: onboarding covered it
                    left > DAY ->                                    // day 2: once, first open of the day
                        i.lastNudgeAt == null || !sameDay(i.lastNudgeAt, i.nowMillis, zone)
                    else -> {
                        // Last 24 h: once on entering it, once more inside the last 6 h.
                        val last = i.lastNudgeAt ?: return true
                        val lastDayStart = a.endsAtMillis - DAY
                        val finalHoursStart = a.endsAtMillis - 6 * HOUR
                        last < lastDayStart || (i.nowMillis >= finalHoursStart && last < finalHoursStart)
                    }
                }
            }

            // Nothing works without Pro, so this is the main conversion surface — still capped.
            Access.TrialExpired -> i.lastNudgeAt == null || i.nowMillis - i.lastNudgeAt >= 4 * HOUR

            Access.Free -> {
                // No enforced trial: the patient cadence with a back-off.
                if (i.nudgeDismissCount >= 6) return false
                val gap = if (i.nudgeDismissCount >= 3) 14 * DAY else 3 * DAY
                if (i.lastNudgeAt != null && i.nowMillis - i.lastNudgeAt < gap) return false
                // A just-finished ride is a better moment than an arbitrary third open.
                if (i.rideCompletedAt != null && i.rideCompletedAt > (i.lastNudgeAt ?: Long.MIN_VALUE)) return true
                val first = i.firstOpenAt ?: return false
                i.appOpenCount >= 3 && i.nowMillis - first >= 2 * DAY
            }
        }
    }

    private fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}
