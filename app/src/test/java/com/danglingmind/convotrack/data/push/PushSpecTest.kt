package com.danglingmind.convotrack.data.push

import com.danglingmind.convotrack.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSpecTest {
    @Test fun emergencyUsesSafetyChannelAndRiderFirst() {
        val s = PushSpec.from("EMERGENCY", null)!!
        assertEquals("safety", s.channelId)
        assertTrue(s.safety)
        assertFalse(s.collapses)
        assertArrayEquals(arrayOf<Any>("Asha", "Sunday run"), s.args("Sunday run", "Asha"))
    }

    @Test fun regroupPicksTextByReason() {
        assertEquals(R.string.push_regroup_food_body, PushSpec.from("REGROUP_CALLED", "FOOD")!!.bodyRes)
        assertEquals(R.string.push_regroup_scenic_body, PushSpec.from("REGROUP_CALLED", "SCENIC")!!.bodyRes)
        assertEquals(R.string.push_regroup_fuel_body, PushSpec.from("REGROUP_CALLED", "FUEL")!!.bodyRes)
        assertEquals("ride_updates", PushSpec.from("REGROUP_CALLED", "FUEL")!!.channelId)
        assertEquals(R.string.push_regroup_generic_body, PushSpec.from("REGROUP_CALLED", null)!!.bodyRes)
    }

    @Test fun startedCollapsesAndUnknownIsIgnored() {
        val s = PushSpec.from("RIDE_STARTED", null)!!
        assertTrue(s.collapses)
        assertArrayEquals(arrayOf<Any>("Run"), s.args("Run", "x"))
        assertNull(PushSpec.from("NOPE", null))
    }
}
