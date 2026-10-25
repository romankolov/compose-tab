package dev.composetab.ui

import dev.composetab.model.*
import kotlin.test.*

class ScoreLayoutTest {
    private fun score() = ScoreData("Test", tracks = listOf(Track("t", "Guitar", measures = listOf(
        Measure("m", 1, beats = listOf(Beat("b", notes = listOf(Note("n", 64, 1, 0, 0)))))
    ))))
    @Test fun createsTargetsForBothRepresentations() {
        val layout = layoutScore(score(), ScoreStyle())
        assertEquals(2, layout.targets.size)
        assertEquals("n", layout.targets.first().event.noteId)
        assertFalse(layout.targets.first().event.tablature)
        assertTrue(layout.targets.last().event.tablature)
        assertTrue(layout.targets.first().bounds.contains(layout.targets.first().bounds.center))
    }
    @Test fun hiddenStaffHasNoStaffTargets() {
        val layout = layoutScore(score(), ScoreStyle(showStaff = false))
        assertEquals(1, layout.targets.size)
        assertTrue(layout.targets.single().event.tablature)
    }
    @Test fun emptyScoreStillHasFiniteBounds() {
        val layout = layoutScore(ScoreData("Empty", tracks = emptyList()), ScoreStyle())
        assertTrue(layout.width > 0); assertTrue(layout.height > 0)
        assertTrue(layout.targets.isEmpty())
    }
    @Test fun clampsViewport() {
        val state = ScoreState(score())
        state.zoomTo(20f); assertEquals(2.5f, state.zoom)
        state.zoomTo(.1f); assertEquals(.5f, state.zoom)
        state.scrollTo(-12f); assertEquals(0f, state.scroll)
    }
}
