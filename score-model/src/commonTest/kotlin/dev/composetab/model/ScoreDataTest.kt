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
    @Test fun dottedRhythmAndInvalidEffects() {
        assertEquals(.75f, Beat("dotted", Duration.Eighth, dotted = true).durationQuarters)
        assertFailsWith<IllegalArgumentException> { Effect.Bend(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { Effect.Bend(-1f) }
        assertFailsWith<IllegalArgumentException> {
            Beat("chord", notes = listOf(Note("a", 64, 1, 0, 0), Note("b", 65, 1, 1, 1)))
        }
    }
}
