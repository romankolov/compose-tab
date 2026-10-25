package dev.composetab.model

import kotlin.test.*

class ScoreDataTest {
    @Test fun restsAndDurations() {
        assertTrue(Beat("rest").isRest)
        assertEquals(.5f, Duration.Eighth.quarters)
    }
    @Test fun validatesInput() {
        assertFailsWith<IllegalArgumentException> { Note("n", 128, 1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(4, 3) }
        assertFailsWith<IllegalArgumentException> {
            ScoreData("Duplicate", tracks = listOf(Track("x", "Guitar", measures = listOf(Measure("x", 1, beats = emptyList())))))
        }
    }
}
