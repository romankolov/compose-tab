package dev.composetab.json

import kotlinx.serialization.json.JsonObject

/** A single track document. Strings and source indices are zero-based, tuning is highest first.
 * Every node retains its original JSON, including unknown extensions, annotations and automations.
 * These values describe the source; use [TabScoreAdapter] to obtain renderer models. */
data class TabDocument(val name: String, val tuning: List<Int>, val measures: List<TabMeasure>, val source: JsonObject)
data class TabMeasure(val numerator: Int, val denominator: Int, val voices: List<TabVoice>, val source: JsonObject)
data class TabVoice(val beats: List<TabBeat>, val source: JsonObject)
data class TabBeat(
    val type: Int, val dotted: Boolean, val durationQuarters: Double,
    val notes: List<TabNote>, val source: JsonObject,
)
data class TabNote(val string: Int, val fret: Int, val source: JsonObject, val sourceIndex: Int = 0)

class TabParseException(val path: String, detail: String, cause: Throwable? = null) :
    IllegalArgumentException("$path: $detail", cause)

data class TabParseLimits(val maxCharacters: Int = 8 * 1024 * 1024, val maxBeats: Int = 200_000, val maxDepth: Int = 128) {
    init { require(maxCharacters > 0 && maxBeats > 0 && maxDepth > 0) }
}
