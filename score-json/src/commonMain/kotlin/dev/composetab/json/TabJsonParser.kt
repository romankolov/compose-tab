package dev.composetab.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/** No networking or platform I/O. One parser can be reused across calls. */
class TabJsonParser(private val limits: TabParseLimits = TabParseLimits()) {
    fun parse(text: String): TabDocument {
        if (text.length > limits.maxCharacters) fail("$", "document exceeds character limit")
        val root = try { Json.parseToJsonElement(text).obj("$") }
        catch (e: SerializationException) { throw TabParseException("$", "invalid JSON", e) }
        val tuning = root.array("tuning", "$").mapIndexed { i, v ->
            v.integer("$.tuning[$i]").also { if (it !in 0..127) fail("$.tuning[$i]", "expected MIDI pitch 0..127") }
        }
        if (tuning.isEmpty() || tuning.size > 16) fail("$.tuning", "expected 1..16 strings")
        root["strings"]?.let { if (it.integer("$.strings") != tuning.size) fail("$.strings", "does not match tuning size") }
        var numerator = 4
        var denominator = 4
        var count = 0
        val measures = root.array("measures", "$").mapIndexed { mi, value ->
            val path = "$.measures[$mi]"
            val m = value.obj(path)
            m["signature"]?.let {
                val sig = it as? JsonArray ?: fail("$path.signature", "expected array")
                if (sig.size != 2) fail("$path.signature", "expected [numerator, denominator]")
                numerator = sig[0].integer("$path.signature[0]")
                denominator = sig[1].integer("$path.signature[1]")
                if (numerator <= 0 || denominator !in listOf(1, 2, 4, 8, 16, 32)) fail("$path.signature", "invalid time signature")
            }
            val voices = m.array("voices", path).mapIndexed { vi, voice ->
                val vp = "$path.voices[$vi]"
                val v = voice.obj(vp)
                val beats = v.array("beats", vp).mapIndexed { bi, beat ->
                    val bp = "$vp.beats[$bi]"
                    if (++count > limits.maxBeats) fail(bp, "document exceeds beat limit")
                    val b = beat.obj(bp)
                    val type = b["type"]?.integer("$bp.type") ?: 4
                    if (type !in listOf(1, 2, 4, 8, 16, 32, 64, 128)) fail("$bp.type", "unsupported note denominator")
                    val dots = b["dots"]?.integer("$bp.dots") ?: if (b.flag("dotted", bp)) 1 else 0
                    if (dots !in 0..4) fail("$bp.dots", "expected 0..4")
                    val factor = 2.0 - 1.0 / (1 shl dots)
                    if (b["tuplet"] != null && b["duration"] == null) fail("$bp.duration", "explicit rational duration required for tuplets")
                    val duration = b["duration"]?.let {
                        val rational = it as? JsonArray ?: fail("$bp.duration", "expected [numerator, denominator]")
                        if (rational.size != 2) fail("$bp.duration", "expected two integers")
                        val n = rational[0].integer("$bp.duration[0]")
                        val d = rational[1].integer("$bp.duration[1]")
                        if (n <= 0 || d <= 0) fail("$bp.duration", "duration must be positive")
                        4.0 * n / d
                    } ?: (4.0 / type * factor)
                    val rest = b.flag("rest", bp)
                    val notes = b.array("notes", bp).mapIndexedNotNull { ni, note ->
                        val np = "$bp.notes[$ni]"
                        val n = note.obj(np)
                        if (n.flag("rest", np)) null else {
                            if (rest) fail(np, "rest beat contains a pitched note")
                            val string = n["string"]?.integer("$np.string") ?: fail("$np.string", "missing string index")
                            val fret = n["fret"]?.integer("$np.fret") ?: fail("$np.fret", "missing fret")
                            if (string !in tuning.indices) fail("$np.string", "string index outside tuning")
                            if (fret < 0 || tuning[string].toLong() + fret > 127) fail("$np.fret", "fret yields invalid MIDI pitch")
                            TabNote(string, fret, n)
                        }
                    }
                    if (notes.map { it.string }.distinct().size != notes.size) fail("$bp.notes", "duplicate string in chord")
                    TabBeat(type, dots > 0, duration, notes, b)
                }
                TabVoice(beats, v)
            }
            TabMeasure(numerator, denominator, voices, m)
        }
        val name = root["name"]?.let { (it as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: fail("$.name", "expected string") } ?: "Untitled track"
        return TabDocument(name, tuning, measures, root)
    }
}

internal fun fail(path: String, message: String): Nothing = throw TabParseException(path, message)
internal fun JsonElement.obj(path: String): JsonObject = this as? JsonObject ?: fail(path, "expected object")
internal fun JsonElement.integer(path: String): Int = (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    ?: fail(path, "expected integer")
internal fun JsonObject.array(key: String, path: String): JsonArray = this[key] as? JsonArray ?: fail("$path.$key", "expected array")
internal fun JsonObject.flag(key: String, path: String): Boolean = this[key]?.let {
    (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.booleanOrNull ?: fail("$path.$key", "expected boolean")
} ?: false
