package com.danglingmind.convotrack.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavVoiceGuideTest {

    private fun input(
        distance: Double,
        speed: Double = 40.0,
        key: Int = 1,
        maneuver: String = "TURN_RIGHT",
        then: String? = null,
        destination: String = "",
    ) = NavVoiceGuide.Input(
        key = key,
        distanceMeters = distance,
        speedKmh = speed,
        instruction = "Turn right onto Nandi Hills Rd",
        maneuver = maneuver,
        thenManeuver = then,
        destinationName = destination,
    )

    @Test
    fun speaksEachApproachLevelOnce() {
        val guide = NavVoiceGuide()
        assertEquals("In 1.2 kilometres, turn right onto Nandi Hills Rd", guide.update(input(1200.0)))
        assertNull(guide.update(input(1100.0)))
        assertNull(guide.update(input(700.0)))
        assertNull(guide.update(input(520.0)))
        assertEquals("In 500 metres, turn right onto Nandi Hills Rd", guide.update(input(480.0)))
        assertNull(guide.update(input(300.0)))
        assertEquals("Turn right onto Nandi Hills Rd, then turn left", guide.update(input(90.0, then = "TURN_LEFT")))
        assertNull(guide.update(input(40.0)))
        assertEquals("Turn right", guide.update(input(10.0)))
        assertNull(guide.update(input(5.0)))
    }

    @Test
    fun skipsFarPromptWhenTheTurnIsAlreadyCloserThanUseful() {
        val guide = NavVoiceGuide()
        // 700 m is past the mid threshold's start but under the 800 m far minimum.
        assertNull(guide.update(input(700.0)))
        assertEquals("In 500 metres, turn right onto Nandi Hills Rd", guide.update(input(480.0)))
    }

    @Test
    fun firstSeenCloseSpeaksOnlyTheNearestLevel() {
        val guide = NavVoiceGuide()
        assertEquals("In 300 metres, turn right onto Nandi Hills Rd", guide.update(input(300.0)))
    }

    @Test
    fun promptsStartEarlierWhenFaster() {
        val guide = NavVoiceGuide()
        // At 120 km/h the main prompt starts at ~667 m rather than 500 m.
        assertEquals("In 900 metres, turn right onto Nandi Hills Rd", guide.update(input(900.0, speed = 120.0)))
        assertEquals("In 650 metres, turn right onto Nandi Hills Rd", guide.update(input(650.0, speed = 120.0)))
    }

    @Test
    fun newKeyStartsFreshPrompts() {
        val guide = NavVoiceGuide()
        assertNotNull(guide.update(input(480.0, key = 1)))
        assertNull(guide.update(input(470.0, key = 1)))
        assertNotNull(guide.update(input(480.0, key = 2)))
    }

    @Test
    fun arrivalNamesTheDestination() {
        val guide = NavVoiceGuide()
        assertEquals(
            "In 400 metres, you will arrive at Nandi Hills",
            guide.update(input(400.0, maneuver = "ARRIVE", destination = "Nandi Hills")),
        )
        assertEquals(
            "You have arrived at Nandi Hills",
            guide.update(input(20.0, maneuver = "ARRIVE", destination = "Nandi Hills")),
        )
    }

    @Test
    fun arrivalFallsBackToYourDestination() {
        val guide = NavVoiceGuide()
        assertEquals("You have arrived at your destination", guide.update(input(20.0, maneuver = "ARRIVE")))
    }

    @Test
    fun repeatCurrentIsAtLeastTheMainPrompt() {
        val guide = NavVoiceGuide()
        assertNull(guide.repeatCurrent())
        // Far away: the main prompt with the distance, never the bare "now" phrase.
        guide.update(input(1200.0))
        assertEquals("In 1.2 kilometres, turn right onto Nandi Hills Rd", guide.repeatCurrent())
        // Right at the junction a repeat is just the short instruction, which is what is useful there.
        guide.update(input(10.0))
        assertEquals("Turn right", guide.repeatCurrent())
    }

    @Test
    fun spokenDistanceRoundsLikeAPerson() {
        assertEquals("50 metres", NavVoiceGuide.spokenDistance(20.0))
        assertEquals("350 metres", NavVoiceGuide.spokenDistance(340.0))
        assertEquals("1 kilometre", NavVoiceGuide.spokenDistance(980.0))
        assertEquals("1.2 kilometres", NavVoiceGuide.spokenDistance(1234.0))
        assertEquals("2 kilometres", NavVoiceGuide.spokenDistance(2000.0))
    }
}
