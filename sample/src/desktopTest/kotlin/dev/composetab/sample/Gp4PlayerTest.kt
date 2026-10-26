package dev.composetab.sample

import dev.composetab.sample.resources.Res
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.*
import kotlin.math.abs

@OptIn(ExperimentalResourceApi::class)
class Gp4PlayerTest {
    @Test fun normalizedDecoderDocumentAndMidiClockAgree() = runBlocking {
        val song = TuxGuitarDecoder.decode(Res.readBytes("files/original-study.gp4"))
        assertEquals(12, song.document.data.tracks.single().measures.size)
        val player = DesktopMidiTransport(enableAudio = false)
        try {
            player.load(song)
            assertTrue(player.status.value.durationQuarters >= 47.9)
            assertFalse(player.status.value.audioAvailable)
            player.play()
            // Await actual sequencer progress, not an assumed audio-device timing delay.
            repeat(40) { if (player.status.value.quarter <= 0) delay(20) }
            assertTrue(player.status.value.quarter > 0)
            val active = song.document.timeline.at(player.status.value.quarter)
            assertTrue(active.positions.isNotEmpty())
            player.pause(); delay(50)
            val paused = player.status.value.quarter
            delay(60)
            assertEquals(paused, player.status.value.quarter, .001)
            player.seek(4.0); delay(50)
            assertTrue(abs(player.status.value.quarter - 4.0) < .05)
            player.stop(); delay(50)
            assertEquals(0.0, player.status.value.quarter, .001)
        } finally { player.close() }
    }
    @Test fun javaReadersDecodeGp3Gp4Gp5AndExportMatchingMidi() = runBlocking {
        val sources = listOf("GP4" to Res.readBytes("files/original-study.gp4")) + listOf("gp3", "gp5").map { extension ->
            extension to javaClass.getResourceAsStream("/fixtures/original-study.$extension")!!.use { it.readBytes() }
        }
        sources.forEach { (label, bytes) ->
            val song = TuxGuitarDecoder.decode(bytes, label)
            assertEquals("Open strings study", song.document.data.title)
            assertEquals("Compose Tab · Original exercise", song.document.data.composer)
            assertEquals(12, song.document.data.tracks.single().measures.size)
            assertEquals(96, song.document.index.notes.size)
            assertEquals(48.0, song.document.timeline.durationQuarters, .001)
            val midi = javax.sound.midi.MidiSystem.getSequence(java.io.ByteArrayInputStream(song.midi))
            val notes = midi.tracks.sumOf { track -> (0 until track.size()).count { i ->
                val message = track[i].message as? javax.sound.midi.ShortMessage
                message?.command == javax.sound.midi.ShortMessage.NOTE_ON && message.data2 > 0
            } }
            assertEquals(96, notes, label)
            val clock = MidiTempoMap.parse(song.midi)
            assertEquals(midi.microsecondLength / 1000.0, clock.millisecondsAt(midi.tickLength.toDouble() / midi.resolution), 1.0)
        }
    }
    @Test fun javaRepeatTimelineAndAndroidTempoClockMatchExport() {
        val bytes = javaClass.getResourceAsStream("/fixtures/repeat-tempo.gp5")!!.use { it.readBytes() }
        val song = TuxGuitarDecoder.decode(bytes)
        assertEquals(56.0, song.document.timeline.durationQuarters, .001)
        val firstBeat = song.document.data.tracks.single().measures.first().beats.first().id
        assertEquals(listOf(0.0, 8.0), song.document.timeline.moments.filter { it.beatId == firstBeat }.map { it.startQuarter })
        val midi = javax.sound.midi.MidiSystem.getSequence(java.io.ByteArrayInputStream(song.midi))
        val clock = MidiTempoMap.parse(song.midi)
        assertEquals(midi.microsecondLength / 1000.0, clock.millisecondsAt(midi.tickLength.toDouble() / midi.resolution), 1.0)
        listOf(0.0, 1.0, 8.0, 20.0, 40.0, 55.0).forEach { q -> assertEquals(q, clock.quarterAt(clock.millisecondsAt(q)), .0001) }
        assertTrue(clock.millisecondsAt(56.0) > 56 * 600.0, "Encoded tempo change must slow playback")
    }
    @Test fun javaReaderPreservesTabArticulationsAndStrokeDirections() {
        val bytes = javaClass.getResourceAsStream("/fixtures/effects.gp5")!!.use { it.readBytes() }
        val beats = TuxGuitarDecoder.decode(bytes).document.data.tracks.single().measures.first().beats
        assertTrue(dev.composetab.model.Effect.HammerOn in beats[0].notes.first().effects)
        assertTrue(dev.composetab.model.Effect.PullOff in beats[1].notes.first().effects)
        assertTrue(beats[2].notes.first().effects.any { it is dev.composetab.model.Effect.Bend })
        assertTrue(beats[3].notes.first().effects.any { it is dev.composetab.model.Effect.Harmonic })
        assertEquals(dev.composetab.model.StrumDirection.Down, beats[4].strum)
        assertEquals(dev.composetab.model.StrumDirection.Up, beats[5].strum)
        assertTrue(dev.composetab.model.Effect.Ghost in beats[6].notes.first().effects)
        assertTrue(beats[7].notes.first().effects.contains(dev.composetab.model.Effect.Harmonic(dev.composetab.model.HarmonicKind.Pinch)))
    }
    @Test fun malformedGpAndMidiFailWithoutMockScore() {
        listOf(byteArrayOf(), "<html>not GP</html>".encodeToByteArray(), ByteArray(64)).forEach { bytes ->
            assertFails { TuxGuitarDecoder.decode(bytes) }
            assertFails { MidiTempoMap.parse(bytes) }
        }
    }
    @Test fun malformedWireDataFailsRatherThanShowingMockScore() {
        assertFails { Gp4DocumentAdapter.parse("{}") }
        assertFails { Gp4DocumentAdapter.parse("not JSON") }
    }
}
