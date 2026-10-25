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
        val song = Gp4DocumentAdapter.parse(Res.readBytes("files/original-study.json").decodeToString())
        assertEquals(12, song.document.data.tracks.single().measures.size)
        val player = DesktopMidiTransport(enableAudio = false)
        try {
            player.load(song)
            assertTrue(player.status.value.durationQuarters >= 48)
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
    @Test fun malformedWireDataFailsRatherThanShowingMockScore() {
        assertFails { Gp4DocumentAdapter.parse("{}") }
        assertFails { Gp4DocumentAdapter.parse("not JSON") }
    }
}
