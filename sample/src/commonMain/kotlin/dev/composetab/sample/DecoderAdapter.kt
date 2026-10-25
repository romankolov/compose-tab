package dev.composetab.sample

import dev.composetab.model.*

/** Sample-only boundary. A real GP5 implementation (or TuxGuitar adapter) belongs here,
 * never inside score-model/score-ui. Run file I/O and decoding off the UI thread. */
interface Gp5Decoder { suspend fun decode(bytes: ByteArray): DecodedSong }
data class DecodedSong(val title: String, val bars: List<List<DecodedBeat>>)
data class DecodedBeat(val eighths: Int, val notes: List<DecodedNote>)
data class DecodedNote(val string: Int, val fret: Int, val technique: String? = null)

class MockGp5Decoder : Gp5Decoder {
    override suspend fun decode(bytes: ByteArray) = DecodedSong("An open road", listOf(
        listOf(DecodedBeat(2, listOf(DecodedNote(2, 5))), DecodedBeat(1, listOf(DecodedNote(1, 3, "hammer"))), DecodedBeat(1, listOf(DecodedNote(1, 5))), DecodedBeat(2, listOf(DecodedNote(1, 7, "bend"))), DecodedBeat(2, listOf(DecodedNote(2, 5)))),
        listOf(DecodedBeat(2, listOf(DecodedNote(3, 4, "slide"))), DecodedBeat(2, listOf(DecodedNote(3, 6))), DecodedBeat(2, emptyList()), DecodedBeat(2, listOf(DecodedNote(2, 7, "vibrato")))),
        listOf(DecodedBeat(2, listOf(DecodedNote(1, 7))), DecodedBeat(2, listOf(DecodedNote(2, 8, "pull"))), DecodedBeat(2, listOf(DecodedNote(2, 5))), DecodedBeat(2, listOf(DecodedNote(3, 7)))),
        listOf(DecodedBeat(4, listOf(DecodedNote(2, 5), DecodedNote(3, 4))), DecodedBeat(4, emptyList())),
    ))
}

fun DecodedSong.toScoreData(): ScoreData {
    val tuning = listOf(64, 59, 55, 50, 45, 40)
    // Simple sharp spelling for this demonstration. Production adapters should respect key,
    // explicit accidental spelling, voices, ties, tuplets and decoder timing semantics.
    val diatonic = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
    val sharps = setOf(1, 3, 6, 8, 10)
    return ScoreData(title, "Compose Tab Sessions", listOf(Track("guitar", "Electric guitar · Standard tuning", tuning,
        bars.mapIndexed { mi, rawBeats -> Measure("m$mi", mi + 1, beats = rawBeats.mapIndexed { bi, raw ->
            Beat("m$mi-b$bi", duration = when (raw.eighths) { 1 -> Duration.Eighth; 2 -> Duration.Quarter; 4 -> Duration.Half; else -> error("Unsupported mock duration") }, notes = raw.notes.mapIndexed { ni, n ->
                val midi = tuning[n.string - 1] + n.fret
                Note("m$mi-b$bi-n$ni", midi, n.string, n.fret,
                    (midi / 12 - 5) * 7 + diatonic[midi % 12] - 2,
                    if (midi % 12 in sharps) Accidental.Sharp else null,
                    listOfNotNull(when (n.technique) { "hammer" -> Effect.HammerOn; "pull" -> Effect.PullOff; "bend" -> Effect.Bend(); "slide" -> Effect.Slide; "vibrato" -> Effect.Vibrato; else -> null }))
            })
        }, repeatEnd = mi == bars.lastIndex) }
    )))
}
