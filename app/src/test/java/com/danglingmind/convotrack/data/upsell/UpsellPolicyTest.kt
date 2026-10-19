package com.danglingmind.convotrack.data.upsell

import com.danglingmind.convotrack.ui.common.Access
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** Same cases the iOS policy was checked against; the two must stay rule-for-rule identical. */
class UpsellPolicyTest {
    private val h = 3_600_000L
    private val d = 86_400_000L
    // Midday UTC so "same calendar day" checks aren't sensitive to the hour offsets used below.
    private val now = 1_800_000_000_000L - (1_800_000_000_000L % d) + 12 * h

    private fun show(
        a: Access, opens: Int = 5, first: Long? = now - 10 * d, last: Long? = null,
        dismiss: Int = 0, onboard: Long? = null, ride: Long? = null,
    ) = UpsellPolicy.shouldShowAppOpenNudge(
        UpsellPolicy.Inputs(a, now, opens, first, last, dismiss, onboard, ride),
        ZoneOffset.UTC,
    )

    @Test fun proNever() = assertEquals(false, show(Access.Pro))
    @Test fun trialDay1() = assertEquals(false, show(Access.Trial(now + 60 * h)))
    @Test fun trialDay2FirstOpen() = assertEquals(true, show(Access.Trial(now + 36 * h)))
    @Test fun trialDay2AlreadyToday() = assertEquals(false, show(Access.Trial(now + 36 * h), last = now - 60_000))
    @Test fun lastDayAfterDay2Nudge() = assertEquals(true, show(Access.Trial(now + 20 * h), last = now - 10 * h))
    @Test fun lastDayAlreadyShown() = assertEquals(false, show(Access.Trial(now + 20 * h), last = now - 2 * h))
    @Test fun final6hAfterEarlierLastDay() = assertEquals(true, show(Access.Trial(now + 5 * h), last = now - 8 * h))
    @Test fun final6hAlreadyShown() = assertEquals(false, show(Access.Trial(now + 5 * h), last = now - 30 * 60_000))
    @Test fun onboardingPaywallBlocks() = assertEquals(false, show(Access.Trial(now + 36 * h), onboard = now - 2 * h))
    @Test fun expiredFirst() = assertEquals(true, show(Access.TrialExpired))
    @Test fun expiredWithin4h() = assertEquals(false, show(Access.TrialExpired, last = now - 3 * h))
    @Test fun expiredAfter4h() = assertEquals(true, show(Access.TrialExpired, last = now - 5 * h))
    @Test fun freeTwoOpens() = assertEquals(false, show(Access.Free, opens = 2))
    @Test fun freeThreeOpensOneDay() = assertEquals(false, show(Access.Free, opens = 3, first = now - d))
    @Test fun freeThreeOpensTwoDays() = assertEquals(true, show(Access.Free, opens = 3, first = now - 2 * d - 60_000))
    @Test fun freeShownTwoDaysAgo() = assertEquals(false, show(Access.Free, last = now - 2 * d))
    @Test fun freeBackoff5Days() = assertEquals(false, show(Access.Free, last = now - 5 * d, dismiss = 3))
    @Test fun freeBackoff15Days() = assertEquals(true, show(Access.Free, last = now - 15 * d, dismiss = 3))
    @Test fun freeSixDismissalsNever() = assertEquals(false, show(Access.Free, last = now - 100 * d, dismiss = 6))
    @Test fun freeRideCompleted() = assertEquals(true, show(Access.Free, opens = 1, first = now - h, last = now - 4 * d, ride = now - h))
}
