package dev.composetab.sample

import dev.composetab.model.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Wire format belongs only to the sample; the UI library knows nothing about GP or JSON. */
data class SampleSong(val document: ScoreDocument, val midi: ByteArray, val warnings: List<String>, val source: String)

object Gp4DocumentAdapter : ScoreAdapter<String> {
    override fun adapt(source: String): ScoreDocument = parse(source).document

    @OptIn(ExperimentalEncodingApi::class)
    fun parse(source: String, sourceName: String = "GP4"): SampleSong {
        val root = Json.parseToJsonElement(source).jsonObject
        require(root.getValue("schemaVersion").jsonPrimitive.int == 1) { "Unsupported decoder schema" }
        val tracks = root.getValue("tracks").jsonArray.map { it.jsonObject }.filter { !it.getValue("percussion").jsonPrimitive.boolean }.map { t ->
            val tuning = t.getValue("tuning").jsonArray.map { it.jsonPrimitive.int }
            val clef = if (tuning.size >= 6) Clef.Treble8vb else if (tuning.max() < 64) Clef.Bass else Clef.Treble
            Track(t.text("id"), t.text("name"), tuning, t.getValue("measures").jsonArray.map { entry ->
                val m = entry.jsonObject
                Measure(m.text("id"), m.getValue("number").jsonPrimitive.int,
                    TimeSignature(m.getValue("numerator").jsonPrimitive.int, m.getValue("denominator").jsonPrimitive.int),
                    beats = m.getValue("beats").jsonArray.map { beatEntry ->
                        val b = beatEntry.jsonObject
                        Beat(b.text("id"), when (b.getValue("duration").jsonPrimitive.int) {
                            1 -> Duration.Whole; 2 -> Duration.Half; 4 -> Duration.Quarter; 8 -> Duration.Eighth
                            16 -> Duration.Sixteenth; 32 -> Duration.ThirtySecond; else -> error("Unsupported visual duration")
                        }, dotted = b.getValue("dotted").jsonPrimitive.boolean,
                            notes = b.getValue("notes").jsonArray.map { noteEntry ->
                                val n = noteEntry.jsonObject
                                val midi = n.getValue("midi").jsonPrimitive.int
                                val writtenMidi = midi + if (clef == Clef.Treble8vb) 12 else 0
                                val pitchClass = writtenMidi % 12
                                val steps = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
                                Note(n.text("id"), midi, n.getValue("string").jsonPrimitive.int, n.getValue("fret").jsonPrimitive.int,
                                    (writtenMidi / 12 - 1) * 7 + steps[pitchClass] - if (clef == Clef.Bass) 18 else 30,
                                    if (pitchClass in setOf(1, 3, 6, 8, 10)) Accidental.Sharp else null,
                                    n.getValue("effects").jsonArray.mapNotNull { effectEntry ->
                                        val effect = effectEntry.jsonObject
                                        when (effect.text("type")) {
                                            "bend" -> Effect.Bend(effect.getValue("semitones").jsonPrimitive.float)
                                            "hammer" -> Effect.HammerOn; "pull" -> Effect.PullOff; "slide" -> Effect.Slide
                                            "vibrato" -> Effect.Vibrato; "palmMute" -> Effect.PalmMute; else -> null
                                        }
                                    })
                            }, offsetQuarters = b.getValue("offset").jsonPrimitive.double,
                            durationScale = b.getValue("scale").jsonPrimitive.double)
                    }, repeatStart = m.getValue("repeatStart").jsonPrimitive.boolean,
                    repeatEnd = m.getValue("repeatEnd").jsonPrimitive.boolean,
                    lengthQuarters = m.getValue("length").jsonPrimitive.double)
            }, clef = clef)
        }
        require(tracks.isNotEmpty()) { "The GP4 file has no pitched tracks to display" }
        val validTracks = tracks.map { it.id }.toSet()
        val timeline = ScoreTimeline(root.getValue("moments").jsonArray.map { it.jsonObject }.filter { it.text("trackId") in validTracks }.map { m ->
            ScoreMoment(m.text("trackId"), m.text("measureId"), m.text("beatId"), m.getValue("start").jsonPrimitive.double,
                m.getValue("duration").jsonPrimitive.double, m.getValue("noteIds").jsonArray.map { it.jsonPrimitive.content }.toSet())
        })
        val score = ScoreData(root.text("title"), root.text("composer"), tracks)
        return SampleSong(ScoreDocument(score, timeline), Base64.decode(root.text("midiBase64")),
            root.getValue("warnings").jsonArray.map { it.jsonPrimitive.content } + "Percussion is played in MIDI but hidden in the pitched-score view.", sourceName)
    }
}

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
