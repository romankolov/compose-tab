package dev.composetab.json

import java.io.File
import kotlin.test.*

/** Opt-in live contract tests. CI downloads the entire two user-supplied documents outside Git. */
class LinkedSamplesTest {
    private fun checkSample(file: String, expectedTuning: List<Int>) {
        val directory = System.getenv("COMPOSE_TAB_JSON_SAMPLES") ?: return
        val text = File(directory, file).readText()
        val document = TabJsonParser().parse(text)
        val result = TabScoreAdapter.adapt(document)
        assertEquals(expectedTuning, document.tuning)
        assertEquals(document.measures.size, result.score.tracks.single().measures.size)
        assertTrue(document.measures.size > 20, "Must test the complete download, not an excerpt")
        val sourceBeats = document.measures.flatMap { it.voices }.flatMap { it.beats }
        val rendered = result.score.tracks.single().measures.flatMap { it.beats }
        assertEquals(sourceBeats.size, rendered.size)
        assertTrue(rendered.flatMap { it.notes }.isNotEmpty())
        assertTrue(rendered.all { it.durationQuarters.isFinite() && it.durationQuarters > 0 })
        println("$file: ${document.measures.size} measures, ${sourceBeats.size} source beats, ${rendered.size} mapped beats, ${result.warnings.size} warnings")
        println("Root fields: ${document.source.keys.sorted()}")
    }
    @Test fun firstLinkedTrack() = checkSample("track-1.json", listOf(64, 59, 55, 50, 45, 40))
    @Test fun secondLinkedTrack() = checkSample("track-2.json", listOf(62, 57, 53, 50, 45, 38))
}
