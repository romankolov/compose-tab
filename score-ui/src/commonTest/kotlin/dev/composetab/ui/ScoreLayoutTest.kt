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
        assertEquals(2, layout.targets.size)
        assertTrue(layout.targets.all { it.event.tablature })
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
    @Test fun verticalWrapsAndHorizontalDoesNot() {
        val track = score().tracks.single()
        val data = ScoreData("Wrap", tracks = listOf(track.copy(measures = List(6) { i ->
            Measure("m$i", i + 1, beats = listOf(Beat("b$i", notes = listOf(Note("n$i", 64, 1, 0, 0)))))
        })))
        val horizontal = layoutScore(data, ScoreStyle(), 650f)
        val vertical = layoutScore(data, ScoreStyle(layoutMode = ScoreLayoutMode.Vertical), 650f)
        assertEquals(1, horizontal.rows.size)
        assertEquals(3, vertical.rows.size)
        assertTrue(vertical.width <= 650f)
        assertTrue(vertical.measures.all { it.x + it.width <= 650f })
        assertTrue(vertical.rows[1].first().y > vertical.rows[0].first().y)
        val resized = layoutScore(data, ScoreStyle(layoutMode = ScoreLayoutMode.Vertical), 350f)
        assertEquals(6, resized.rows.size)
        assertEquals("n3", resized.hitTest(resized.targets.first { it.event.noteId == "n3" }.bounds.center)?.event?.noteId)
    }
    @Test fun pointersAndSelectionHaveIndependentStyles() {
        val state = ScoreState(score())
        state.selectNotes("n")
        state.setPlaybackQuarter(.25)
        assertEquals(setOf("n"), state.playback?.noteIds)
        val style = ScoreStyle(selectedHighlight = ScoreHighlightStyle(HighlightShape.Rectangle),
            playhead = ScoreHighlightStyle(HighlightShape.VerticalLine))
        val measure = layoutScore(score(), style).measures.single()
        val marks = measureHighlights(measure, state.selectedNoteIds, state.playback, style)
        assertTrue(marks.any { it.kind == HighlightKind.Selection && it.style.shape == HighlightShape.Rectangle })
        assertEquals(1, marks.count { it.kind == HighlightKind.Playhead })
        state.clearPlayback()
        assertEquals(setOf("n"), state.selectedNoteIds)
    }
}
