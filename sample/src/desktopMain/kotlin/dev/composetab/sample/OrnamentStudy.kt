package dev.composetab.sample

import dev.composetab.model.*

/** Original model-only engraving exercise; no decoder or audio dependencies. */
fun ornamentStudy(): ScoreData {
    fun note(id: String, fret: Int, string: Int, tie: String? = null): Note {
        val midi = listOf(64, 59, 55, 50, 45, 40)[string - 1] + fret
        val written = midi + 12
        val steps = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
        return Note(id, midi, string, fret, (written / 12 - 1) * 7 + steps[written % 12] - 30, tieFrom = tie)
    }
    val frets = listOf(listOf(5, 5, 7, 7), listOf(7, 7, 9, 9), listOf(9, 9, 12, 12), listOf(12, 12, 14, 14))
    val measures = frets.mapIndexed { mi, row ->
        val beats = row.mapIndexed { bi, fret ->
            val tie = when {
                bi == 0 && mi > 0 -> "n${mi - 1}:3"
                bi == 1 || bi == 3 -> "n$mi:${bi - 1}"
                else -> null
            }
            Beat("b$mi:$bi", notes = listOf(note("n$mi:$bi", fret, 1, tie), note("c$mi:$bi", fret, 3, tie?.replaceFirst("n", "c"))))
        }
        val grace = if (mi == 0) listOf(
            Beat("gr", Duration.Eighth, grace = GraceKind.BeforeBeat),
            Beat("g0", Duration.Eighth, notes = listOf(note("gn0", 2, 1)), grace = GraceKind.BeforeBeat),
            Beat("g1", Duration.Eighth, notes = listOf(note("gn1", 3, 1)), grace = GraceKind.OnBeat)) else emptyList()
        Measure("m$mi", mi + 1, beats = grace + beats)
    }
    return ScoreData("Tie and grace study", tracks = listOf(Track("t", "Ties across bars and systems · small before/on-beat graces", measures = measures, clef = Clef.Treble8vb)))
}
