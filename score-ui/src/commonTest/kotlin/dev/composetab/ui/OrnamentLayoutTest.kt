package dev.composetab.ui

import dev.composetab.model.*
import kotlin.test.*

class OrnamentLayoutTest {
    private fun data(): ScoreData {
        val a = Note("a", 64, 1, 0, 0)
        return ScoreData("Ornaments", tracks = listOf(Track("t", "Guitar", measures = listOf(
            Measure("m0", 1, beats = listOf(
                Beat("g0", Duration.Eighth, notes = listOf(a.copy(id = "g")), grace = GraceKind.BeforeBeat),
                Beat("g1", Duration.Eighth, notes = listOf(a.copy(id = "h")), grace = GraceKind.OnBeat),
                Beat("b0", Duration.Whole, notes = listOf(a)))),
            Measure("m1", 2, beats = listOf(Beat("b1", Duration.Whole, notes = listOf(a.copy(id = "b", tieFrom = "a")))))
        ))))
    }
    @Test fun graceGlyphsPrecedeAnchorRemainSelectableAndDoNotBeamWithMainNotes() {
        val layout = layoutScore(data(), ScoreStyle(showStaff = false))
        val beats = layout.measures.first().beats
        assertEquals(listOf(0f, 0f, 0f), beats.map { it.onset })
        assertTrue(beats[0].x < beats[1].x && beats[1].x < beats[2].x)
        assertTrue(layout.measures.first().beams.isEmpty())
        val target = layout.targets.first { it.event.noteId == "g" }
        assertEquals("g", layout.hitTest(target.bounds.center)?.event?.noteId)
    }
    @Test fun graceDoesNotBreakMetricalBeamGroup() {
        val main = Beat("b", Duration.Eighth, notes = listOf(Note("n", 64, 1, 0, 0)))
        val beats = listOf(PlacedBeat(main, 80f, 0f),
            PlacedBeat(main.copy(id = "g", grace = GraceKind.BeforeBeat), 100f, .5f),
            PlacedBeat(main.copy(id = "next"), 120f, .5f))
        assertEquals(listOf(BeamSegment(84f, 124f, 0)), beamSegments(beats, TimeSignature()))
    }
    @Test fun tiesSplitAcrossWrappedSystemsForStaffAndTab() {
        val layout = layoutScore(data(), ScoreStyle(layoutMode = ScoreLayoutMode.Vertical), 350f)
        assertTrue(layout.measures[1].y > layout.measures[0].y)
        assertEquals(2, layout.measures[0].ties.size)
        assertEquals(2, layout.measures[1].ties.size)
        layout.measures.forEach { m -> m.ties.forEach { assertTrue(it.to.x > it.from.x) } }
    }
    @Test fun tabOnlyTiesUseCustomStringSpacingNotStaffCoordinates() {
        val layout = layoutScore(data(), ScoreStyle(showStaff = false, tabStringSpacing = 12f))
        assertEquals(1, layout.measures.first().ties.size)
        assertEquals(layout.measures.first().y + 8f, layout.measures.first().ties.single().from.y)
    }
}
