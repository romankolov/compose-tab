package dev.composetab.ui

import dev.composetab.model.*

/** Conventional note-local tab spelling: ghost frets in (), harmonics in <>. */
internal fun tabFretLabel(note: Note): String {
    val harmonic = note.effects.any { it is Effect.Harmonic }
    val fret = if (harmonic) "<${note.fret}>" else note.fret.toString()
    return if (Effect.Ghost in note.effects) "($fret)" else fret
}
