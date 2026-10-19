package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.data.net.RideParticipant
import com.danglingmind.convotrack.data.socket.SplitEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitAlertInfoTest {

    private fun row(id: String, name: String, km: Double, me: Boolean = false) =
        NavLeaderboardRow(id = id, rank = 1, name = name, avatarUrl = null, distanceToGoalKm = km, isMe = me)

    @Test
    fun gapComesFromTheLiveLeaderboardWhenPositive() {
        val rows = listOf(row("lead", "Asha Rao", 10.0), row("back", "Ravi Kumar", 12.4))
        val info = splitAlertInfo(SplitEvent(gapMeters = 1000.0, leaderId = "lead", lastRiderId = "back"), rows, emptyList(), "me")
        assertEquals("Ravi", info.riderName)
        assertEquals(2400.0, info.gapMeters, 0.5)
    }

    @Test
    fun fallsBackToTheServerGapBeforeRowsHavePositions() {
        // Both rows still seeded with the same route length: live gap is 0, so use the server's.
        val rows = listOf(row("lead", "Asha", 50.0), row("back", "Ravi", 50.0))
        val info = splitAlertInfo(SplitEvent(gapMeters = 1800.0, leaderId = "lead", lastRiderId = "back"), rows, emptyList(), "me")
        assertEquals(1800.0, info.gapMeters, 0.001)
    }

    @Test
    fun namesYouWhenYouAreTheOneBehind() {
        val info = splitAlertInfo(SplitEvent(gapMeters = 500.0, leaderId = "lead", lastRiderId = "me"), emptyList(), emptyList(), "me")
        assertEquals("You", info.riderName)
    }

    @Test
    fun usesTheRosterWhenTheRowIsMissing() {
        val roster = listOf(RideParticipant(userId = "back", name = "Mia Chen"))
        val info = splitAlertInfo(SplitEvent(gapMeters = 500.0, leaderId = "lead", lastRiderId = "back"), emptyList(), roster, "me")
        assertEquals("Mia", info.riderName)
    }

    @Test
    fun formatsTheGapForTheBanner() {
        assertEquals("10 m", formatSplitGap(4.0))
        assertEquals("350 m", formatSplitGap(347.0))
        assertEquals("2.4 km", formatSplitGap(2400.0))
    }
}
