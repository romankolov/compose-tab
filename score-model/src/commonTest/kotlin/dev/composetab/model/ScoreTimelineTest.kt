package dev.composetab.model

import kotlin.test.*

class ScoreTimelineTest {
    @Test fun activeChordsTracksRestsAndEndAreIndexed() {
        val timeline = ScoreTimeline(listOf(
            ScoreMoment("g", "m", "chord", 0.0, 1.0, setOf("a", "b")),
            ScoreMoment("c", "mc", "long", 0.0, 3.0, setOf("c")),
            ScoreMoment("g", "m", "rest", 1.0, 1.0, emptySet()),
        ))
        assertEquals(setOf("a", "b", "c"), timeline.at(.5).noteIds)
        assertEquals(.5f, timeline.at(.5).positions.first { it.beatId == "chord" }.fraction)
        assertEquals(setOf("c"), timeline.at(1.0).noteIds)
        assertTrue(timeline.at(3.0).positions.isEmpty())
        assertFailsWith<IllegalArgumentException> { timeline.at(Double.NaN) }
    }
    @Test fun decoderTimingPreservesTupletsAndPickups() {
        val score = ScoreData("Timing", tracks = listOf(Track("t", "Test", measures = listOf(
            Measure("m", 1, lengthQuarters = 1.0, beats = List(3) { Beat("b$it", Duration.Eighth, durationScale = 2.0 / 3.0) }),
            Measure("m2", 2, beats = listOf(Beat("next"))),
        ))))
        val timeline = ScoreTimeline.from(score)
        assertEquals(1.0, timeline.moments.last().startQuarter, .00001)
        assertEquals(1.0 / 3, timeline.moments.first().durationQuarters, .00001)
    }
    @Test fun repeatedPassesReuseStableIds() {
        val moments = List(3) { ScoreMoment("t", "m", "b", it.toDouble(), 1.0, setOf("n")) }
        assertEquals("b", ScoreTimeline(moments).at(2.5).positions.single().beatId)
    }
}
