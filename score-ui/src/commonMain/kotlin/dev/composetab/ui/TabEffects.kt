package dev.composetab.ui

import dev.composetab.model.*

/** Conventional note-local tab spelling: ghost frets in (), harmonics in <>. */
internal fun tabFretLabel(note: Note): String {
    if (Effect.DeadNote in note.effects || Effect.Slap in note.effects) return "x"
    val harmonic = note.effects.filterIsInstance<Effect.Harmonic>().firstOrNull()
    val fret = when {
        harmonic == null -> note.fret.toString()
        harmonic.kind == HarmonicKind.Natural -> "<${note.fret}>"
        else -> "◆${note.fret}"
    }
    return if (Effect.Ghost in note.effects) "($fret)" else fret
}

/** Repeat dots sit around the centre of the actual staff/string span. */
internal data class RepeatSpan(val top: Float, val bottom: Float, val upperDot: Float, val lowerDot: Float)
internal fun repeatSpan(top: Float, lines: Int, spacing: Float): RepeatSpan {
    val bottom = top + (lines - 1).coerceAtLeast(0) * spacing
    val centre = (top + bottom) / 2
    return RepeatSpan(top, bottom, centre - spacing / 2, centre + spacing / 2)
}

/** Slurs enclose the full source and destination glyphs, including multi-digit frets. */
internal fun legatoEdges(startX: Float, startWidth: Float, endX: Float, endWidth: Float): Pair<Float, Float> =
    (startX - startWidth / 2 - 2f) to (endX + endWidth / 2 + 2f)

internal fun harmonicCaption(notes: List<Note>): String = notes.flatMap { it.effects }
    .filterIsInstance<Effect.Harmonic>().mapNotNull { when (it.kind) {
        HarmonicKind.Natural -> null
        HarmonicKind.Artificial -> "A.H."
        HarmonicKind.Tapped -> "T.H."
        HarmonicKind.Pinch -> "P.H."
        HarmonicKind.Semi -> "S.H."
    } }.distinct().joinToString(" / ")

/** Dedicated clef lane: start-repeat dots occupy x+9; letters stay at x+24. */
internal data class TabClefLayout(val centerX: Float, val centersY: List<Float>, val fontSize: Float)
internal fun tabClefLayout(x: Float, top: Float, strings: Int, spacing: Float): TabClefLayout {
    val stringHeight = (strings - 1).coerceAtLeast(0) * spacing
    val height = stringHeight.coerceAtLeast(30f)
    val center = top + stringHeight / 2
    val font = minOf(14f, height / 3)
    return TabClefLayout(x + 24f, listOf(center - height / 2 + font / 2, center, center + height / 2 - font / 2), font)
}
