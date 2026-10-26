package dev.composetab.json

import dev.composetab.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class TabJsonTest {
    private fun track(beats: String, extra: String = "") = """{"name":"Original exercise","tuning":[64,59,55,50,45,40],"measures":[{"voices":[{"beats":[$beats]}]}]$extra}"""
    private val parser = TabJsonParser()
    @Test fun mapsZeroBasedStringsAndStableIds() {
        val doc = parser.parse(track("""{"type":8,"duration":[1,8],"notes":[{"string":0,"fret":3}]}"""))
        val a = TabScoreAdapter.adapt(doc)
        val b = doc.toScoreData()
        assertEquals(a.score, b)
        val note = b.tracks.single().measures.single().beats.single().notes.single()
        assertEquals(1, note.string); assertEquals(67, note.midi); assertEquals(9, note.staffStep)
        assertEquals(TabSourceLocation(0, 0, 0, 0), a.locations[note.id])
    }
    @Test fun rationalDurationOverridesDisplayTypeAndDots() {
        val b = parser.parse(track("""{"type":8,"dotted":true,"duration":[1,12],"notes":[{"rest":true}]}""")).toScoreData().tracks[0].measures[0].beats[0]
        assertEquals(1.0 / 3, b.durationQuarters.toDouble(), 0.00001)
        assertTrue(b.isRest)
    }
    @Test fun simultaneousVoicesHaveIndependentOffsetsAndInheritedMeter() {
        val doc = parser.parse("""{"tuning":[64],"measures":[{"signature":[3,4],"voices":[{"beats":[{"notes":[],"type":2}]},{"beats":[{"notes":[{"string":0,"fret":0}],"type":4}]}]},{"voices":[]}]}""")
        val measures = doc.toScoreData().tracks[0].measures
        assertEquals(listOf(0.0, 0.0), measures[0].beats.map { it.offsetQuarters })
        assertEquals(TimeSignature(3, 4), measures[1].time)
        assertEquals(3.0, measures[1].lengthQuarters)
    }
    @Test fun retainsUnknownMetadataAndReportsUnsupportedEngraving() {
        val doc = parser.parse(track("""{"type":4,"notes":[{"string":1,"fret":7,"tie":true,"harmonic":"natural","future":{"a":1}}]}""", """, "automations":{"tempo":[{"bpm":90}]}"""))
        val result = TabScoreAdapter.adapt(doc)
        assertNotNull(doc.source["automations"])
        assertNotNull(doc.measures[0].voices[0].beats[0].notes[0].source["future"])
        assertTrue(result.warnings.any { it.path.endsWith(".tie") })
        assertTrue(result.score.tracks[0].measures[0].beats[0].notes[0].effects.single() is Effect.Harmonic)
    }
    @Test fun hpDirectionUsesFollowingFret() {
        val doc = parser.parse(track("""{"notes":[{"string":0,"fret":5,"hp":true}]},{"notes":[{"string":0,"fret":7,"hp":true}]},{"notes":[{"string":0,"fret":5}]}"""))
        val beats = doc.toScoreData().tracks[0].measures[0].beats
        assertEquals(listOf(Effect.HammerOn), beats[0].notes[0].effects)
        assertEquals(listOf(Effect.PullOff), beats[1].notes[0].effects)
    }
    @Test fun graceIsRetainedButDoesNotShiftMainBeat() {
        val result = TabScoreAdapter.adapt(parser.parse(track("""{"graceNote":"onBeat","type":64,"notes":[{"rest":true}]},{"type":4,"notes":[]}""")))
        assertEquals(0.0, result.score.tracks[0].measures[0].beats.single().offsetQuarters)
        assertEquals(2, result.document.measures[0].voices[0].beats.size)
        assertTrue(result.warnings.any { it.message.contains("Grace") })
    }
    @Test fun shortDurationsUseScaleWithoutLosingTiming() {
        val beat = parser.parse(track("""{"type":64,"duration":[1,64],"notes":[]}""")).toScoreData().tracks[0].measures[0].beats[0]
        assertEquals(.0625f, beat.durationQuarters)
    }
    @Test fun validatesPathsRatherThanClampingBadNotes() {
        val error = assertFailsWith<TabParseException> { parser.parse(track("""{"notes":[{"string":6,"fret":2}]}""")) }
        assertTrue(error.path.endsWith(".notes[0].string"))
        assertFailsWith<TabParseException> { parser.parse(track("""{"duration":[1,0],"notes":[]}""")) }
        assertFailsWith<TabParseException> { parser.parse(track("""{"notes":[{"string":0,"fret":1},{"string":0,"fret":2}]}""")) }
        assertFailsWith<TabParseException> { parser.parse("{") }
        assertFailsWith<TabParseException> { parser.parse(track("""{"type":"8","notes":[]}""")) }
    }
    @Test fun sourceIndicesSurviveRestPlaceholders() {
        val result = TabScoreAdapter.adapt(parser.parse(track("""{"notes":[{"rest":true},{"string":0,"fret":0}]}""")))
        val note = result.score.tracks[0].measures[0].beats[0].notes[0]
        assertEquals(1, result.locations.getValue(note.id).note)
    }
    @Test fun nestingLimitIncludesUnknownExtensionsButNotStringContent() {
        assertFailsWith<TabParseException> { TabJsonParser(TabParseLimits(maxDepth = 2)).parse("""{"extra":[[[]]]}""") }
        assertNotNull(parser.parse(track("""{"notes":[],"text":"[[[[{{{{"}""")))
    }
    @Test fun enforcesLimits() {
        assertFailsWith<TabParseException> { TabJsonParser(TabParseLimits(maxCharacters = 1)).parse("{}") }
        assertFailsWith<TabParseException> { TabJsonParser(TabParseLimits(maxBeats = 1)).parse(track("""{"notes":[]},{"notes":[]}""")) }
    }
}
