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
    @Test fun beamGroupsRespectSimpleAndCompoundMeter() {
        val eighths = List(6) { index ->
            PlacedBeat(Beat("b$index", Duration.Eighth, notes = listOf(Note("n$index", 64, 1, 0, 0))), index * 40f, index * .5f)
        }
        val simple = beamSegments(eighths, TimeSignature(3, 4))
        assertEquals(3, simple.size) // Three pairs, no trailing flags.
        assertEquals(44f, simple.first().to)
        val compound = beamSegments(eighths, TimeSignature(6, 8))
        assertEquals(4, compound.size) // Two groups of three eighth notes.
        assertFalse(compound.any { it.from == 84f && it.to == 124f })
    }
    @Test fun cullingAndHitTestUseSameGeometry() {
        val layout = layoutScore(score(), ScoreStyle())
        assertTrue(layout.visibleMeasures(10000f, 0f, 11000f, 1000f).isEmpty())
        assertEquals("n", layout.hitTest(layout.targets.first().bounds.center)?.event?.noteId)
    }
    @Test fun viewportRejectsNonFiniteInput() {
        val state = ScoreState(score())
        state.zoomTo(Float.NaN); state.scrollTo(Float.POSITIVE_INFINITY)
        assertEquals(1f, state.zoom); assertEquals(0f, state.scroll)
    }
}
