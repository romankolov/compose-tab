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
