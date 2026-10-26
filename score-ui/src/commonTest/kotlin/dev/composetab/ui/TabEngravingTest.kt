package dev.composetab.ui

import dev.composetab.model.*
import kotlin.test.*

class TabEngravingTest {
    @Test fun repeatsUseActualStringCountAndCustomSpacing() {
        for (strings in listOf(4, 6, 7, 8)) for (spacing in listOf(12f, 18f, 32f)) {
            val span = repeatSpan(70f, strings, spacing)
            assertEquals(70f + (strings - 1) * spacing, span.bottom)
            assertEquals(70f + (strings - 1) * spacing / 2, (span.upperDot + span.lowerDot) / 2)
            assertEquals(spacing, span.lowerDot - span.upperDot)
        }
        assertEquals(110f, repeatSpan(70f, 5, 10f).bottom)
    }

    @Test fun legatoEnclosesBothFretsRatherThanTheirInnerEdges() {
        val (left, right) = legatoEdges(100f, 8f, 180f, 16f)
        assertEquals(94f, left)
        assertEquals(190f, right)
        assertTrue(left < 100f - 8f / 2)
        assertTrue(right > 180f + 16f / 2)
    }

    @Test fun harmonicCaptionsAreOneDeduplicatedBeatAnnotation() {
        fun note(id: String, kind: HarmonicKind) = Note(id, 64, 1, 12, 0, effects = listOf(Effect.Harmonic(kind)))
        assertEquals("A.H. / P.H.", harmonicCaption(listOf(note("a", HarmonicKind.Artificial),
            note("b", HarmonicKind.Artificial), note("c", HarmonicKind.Pinch), note("d", HarmonicKind.Natural))))
        assertEquals("", harmonicCaption(listOf(note("n", HarmonicKind.Natural))))
    }
}
