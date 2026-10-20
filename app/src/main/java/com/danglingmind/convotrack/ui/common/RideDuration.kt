package com.danglingmind.convotrack.ui.common

import java.util.Locale

/**
 * The one place a ride duration becomes text. Port of iOS `RideDuration.swift`.
 *
 * There were two independent implementations on iOS and they had already drifted: the history list
 * formatted EVERY duration as `"%d:%02d HRS"` unconditionally, so a 44-minute ride rendered
 * **"0:44 HRS"** — a minutes-long ride labelled in hours. The summary screen had its own pair which
 * was at least internally consistent, but anything over an hour came out as "1:44" beside a "HRS"
 * label, and that is ambiguous on sight: 1.44 hours? one hour forty-four? a clock time?
 *
 * Android had the same split — `formatDuration` and `HistoryRide.durationLabel` were separate
 * near-copies. Value and unit are returned together here so they can never be derived from
 * different inputs, and every surface reads from this single switch.
 *
 * What the number MEANS is decided server-side (`summaryService.ts`): actual wall-clock
 * `endedAt − startedAt`, which starts when the leader taps START RIDE and includes any time paused
 * or stopped. This type only renders it.
 */
object RideDuration {

    /** Big-value + small-caps-unit pair, for metric cards and the share card. */
    fun stat(seconds: Int?): Pair<String, String> {
        if (seconds == null || seconds <= 0) return "--" to ""
        return when {
            seconds < 60 -> "$seconds" to "SEC"
            seconds < 3600 -> "${seconds / 60}" to "MIN"
            // The unit spells out the FORMAT, because "1:44 HRS" is exactly the ambiguity that
            // started this. Hours are deliberately not capped at 24 — a ride nobody remembered to
            // end reads "26:10", which looks wrong because it is wrong.
            else -> String.format(Locale.US, "%d:%02d", seconds / 3600, (seconds % 3600) / 60) to "H:MM"
        }
    }

    /** Single inline string, for rows with no separate unit slot. */
    fun inline(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "--"
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min"
            else -> String.format(Locale.US, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60)
        }
    }
}
