package dev.composetab.ui

import dev.composetab.model.*
import kotlin.test.*

class TabClefCursorTest {
    @Test fun clefIsCenteredSpansStringsAndClearsStartRepeatDots() {
        for (strings in listOf(4, 6, 7, 8)) for (gap in listOf(10f, 18f, 32f)) {
            val clef = tabClefLayout(32f, 70f, strings, gap)
            val span = (strings - 1) * gap
            assertEquals(70f + span / 2, clef.centersY[1])
            assertEquals(70f, clef.centersY.first() - clef.fontSize / 2)
            assertEquals(70f + span, clef.centersY.last() + clef.fontSize / 2)
            assertTrue(clef.centerX - clef.fontSize / 2 > 32f + 3 + 6 + 1.8f)
        }
    }
    @Test fun incomingWrappedTabTieClearsLetterLane() {
        val first = Note("a", 64, 1, 0, 7)
        val score = ScoreData("Wrapped", tracks = listOf(Track("t", "Guitar", measures = listOf(
            Measure("m0", 1, beats = listOf(Beat("b0", Duration.Whole, notes = listOf(first)))),
            Measure("m1", 2, beats = listOf(Beat("b1", Duration.Whole, notes = listOf(first.copy(id = "b", tieFrom = "a")))))
        ))))
        val style = ScoreStyle(showStaff = false, layoutMode = ScoreLayoutMode.Vertical)
        val m = layoutScore(score, style, 350f).measures.last()
        val clef = tabClefLayout(m.x, m.y, m.track.tuning.size, style.tabStringSpacing)
        assertTrue(m.startsSystem)
        assertTrue(m.ties.single().from.x > clef.centerX + clef.fontSize / 2)
    }
    private fun placed(style: ScoreStyle): PlacedMeasure {
        val data = ScoreData("Cursor", tracks = listOf(Track("t", "Guitar", measures = listOf(Measure("m", 1, beats = listOf(
            Beat("b", notes = listOf(Note("n", 76, 1, 12, 14), Note("low", 52, 6, 12, 0))),
            Beat("next", notes = listOf(Note("other", 64, 1, 0, 7)))
        ))))))
        return layoutScore(data, style).measures.single()
    }
    @Test fun capsuleEnclosesWholeChordAndDoesNotSlideOffSoundingNotes() {
        for (staff in listOf(true, false)) {
            val style = ScoreStyle(showStaff = staff, playhead = ScoreHighlightStyle(HighlightShape.Capsule))
            val m = placed(style)
            val playback = ScorePlaybackPosition(listOf(ScorePosition("b", .9f)), setOf("n", "low"))
            val cursor = measureHighlights(m, emptySet(), playback, style).single { it.kind == HighlightKind.Playhead }
            assertEquals(m.beats.first().x, cursor.bounds.center.x)
            m.targets.filter { !it.rhythm && it.event.beatId == "b" }.forEach { target ->
                assertTrue(cursor.bounds.contains(target.bounds.topLeft))
                assertTrue(cursor.bounds.contains(target.bounds.bottomRight))
            }
        }
    }
    @Test fun capsuleWorksWithOnlyPlayingIdsAndPreservesOtherCursorStyles() {
        val style = ScoreStyle(showStaff = false, playhead = ScoreHighlightStyle(HighlightShape.Capsule))
        val m = placed(style)
        assertEquals(1, measureHighlights(m, emptySet(), ScorePlaybackPosition(noteIds = setOf("n", "low")), style)
            .count { it.kind == HighlightKind.Playhead })
        val lineStyle = style.copy(playhead = ScoreHighlightStyle(HighlightShape.VerticalLine))
        val cursor = measureHighlights(m, emptySet(), ScorePlaybackPosition(listOf(ScorePosition("b", .5f))), lineStyle)
            .single { it.kind == HighlightKind.Playhead }
        assertTrue(cursor.bounds.center.x > m.beats.first().x)
        assertEquals(lineStyle.playhead.lineWidth, cursor.bounds.width)
    }
}
