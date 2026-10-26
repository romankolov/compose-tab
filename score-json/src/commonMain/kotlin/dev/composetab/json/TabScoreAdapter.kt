package dev.composetab.json

import dev.composetab.model.*
import kotlinx.serialization.json.*

data class TabAdapterOptions(val title: String? = null, val composer: String = "", val idPrefix: String = "json", val clef: Clef = Clef.Treble8vb) {
    init { require(idPrefix.isNotBlank()) }
}
data class TabWarning(val path: String, val message: String)
data class TabSourceLocation(val measure: Int, val voice: Int? = null, val beat: Int? = null, val note: Int? = null)
data class TabScoreResult(
    val score: ScoreData, val document: TabDocument, val warnings: List<TabWarning>,
    /** Stable renderer ID -> zero-based source indices (for clicks, selection and metadata). */
    val locations: Map<String, TabSourceLocation>,
)

/** Pure adaptation; source timing is authoritative. No audio, repeat expansion or networking.
 * Unsupported engraving remains in document.source and is reported instead of silently discarded. */
object TabScoreAdapter {
    fun adapt(document: TabDocument, options: TabAdapterOptions = TabAdapterOptions()): TabScoreResult {
        val warnings = mutableListOf<TabWarning>()
        val locations = mutableMapOf<String, TabSourceLocation>()
        val prefix = options.idPrefix
        val measures = document.measures.mapIndexed { mi, m ->
            val mid = "$prefix:m$mi"
            val mp = "$.measures[$mi]"
            locations[mid] = TabSourceLocation(mi)
            val beats = m.voices.flatMapIndexed { vi, v ->
                var offset = 0.0
                v.beats.mapIndexedNotNull { bi, b ->
                    val bp = "$mp.voices[$vi].beats[$bi]"
                    if (b.source["graceNote"] != null) {
                        warnings += TabWarning(bp, "Grace beat retained in source but omitted from rendering/timing")
                        return@mapIndexedNotNull null
                    }
                    val bid = "$mid:v$vi:b$bi"
                    locations[bid] = TabSourceLocation(mi, vi, bi)
                    val notes = b.notes.map { n ->
                        val np = "$bp.notes[${n.sourceIndex}]"
                        val nid = "$bid:n${n.sourceIndex}"
                        locations[nid] = TabSourceLocation(mi, vi, bi, n.sourceIndex)
                        val midi = document.tuning[n.string] + n.fret
                        val effects = mutableListOf<Effect>()
                        fun flag(key: String, effect: Effect) { if (n.source.flag(key, np)) effects += effect }
                        flag("dead", Effect.DeadNote); flag("ghost", Effect.Ghost)
                        flag("vibrato", Effect.Vibrato); flag("palmMute", Effect.PalmMute)
                        if (b.source.flag("palmMute", bp) && Effect.PalmMute !in effects) effects += Effect.PalmMute
                        if (n.source.flag("hp", np)) {
                            val next = v.beats.drop(bi + 1).firstNotNullOfOrNull { next -> next.notes.firstOrNull { it.string == n.string } }
                            if (next != null) effects += if (next.fret >= n.fret) Effect.HammerOn else Effect.PullOff
                            else warnings += TabWarning(np, "H/P has no following same-string target")
                        }
                        n.source["harmonic"]?.let { harmonic ->
                            val kind = when ((harmonic as? JsonPrimitive)?.content) {
                                "natural" -> HarmonicKind.Natural; "artificial" -> HarmonicKind.Artificial
                                "pinch" -> HarmonicKind.Pinch; "tapped" -> HarmonicKind.Tapped; "semi" -> HarmonicKind.Semi
                                else -> null
                            }
                            if (kind != null) effects += Effect.Harmonic(kind)
                            else warnings += TabWarning("$np.harmonic", "Unrecognised harmonic retained in source")
                        }
                        listOf("tie", "staccato", "bend", "slide", "slap").filter { n.source[it] != null && n.source[it] != JsonPrimitive(false) }.forEach {
                            warnings += TabWarning("$np.$it", "Source $it retained; no mapping implemented")
                        }
                        val written = midi + if (options.clef == Clef.Treble8vb) 12 else 0
                        val pc = written % 12
                        val steps = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
                        val bottom = if (options.clef == Clef.Bass) 2 * 7 + 4 else 4 * 7 + 2
                        Note(nid, midi, n.string + 1, n.fret, (written / 12 - 1) * 7 + steps[pc] - bottom,
                            if (pc in listOf(1, 3, 6, 8, 10)) Accidental.Sharp else null, effects)
                    }
                    listOf("text", "chord", "letRing", "tuplet").filter { b.source[it] != null && b.source[it] != JsonPrimitive(false) }.forEach {
                        warnings += TabWarning("$bp.$it", "Source $it retained; annotation not engraved (rational timing preserved)")
                    }
                    val duration = when (b.type) {
                        1 -> Duration.Whole; 2 -> Duration.Half; 4 -> Duration.Quarter
                        8 -> Duration.Eighth; 16 -> Duration.Sixteenth; else -> Duration.ThirtySecond
                    }
                    val strum = when {
                        b.source["upStroke"] != null -> StrumDirection.Up
                        b.source["downStroke"] != null -> StrumDirection.Down
                        else -> null
                    }
                    Beat(bid, duration, b.dotted, notes, offset,
                        b.durationQuarters / (duration.quarters * if (b.dotted) 1.5 else 1.0), strum).also { offset += b.durationQuarters }
                }
            }.sortedBy { it.offsetQuarters }
            Measure(mid, mi + 1, TimeSignature(m.numerator, m.denominator), beats,
                lengthQuarters = m.numerator * 4.0 / m.denominator)
        }
        return TabScoreResult(ScoreData(options.title ?: document.name, options.composer, listOf(
            Track("$prefix:track", document.name, document.tuning, measures, options.clef))), document, warnings, locations)
    }
}

fun TabDocument.toScoreData(options: TabAdapterOptions = TabAdapterOptions()): ScoreData = TabScoreAdapter.adapt(this, options).score
