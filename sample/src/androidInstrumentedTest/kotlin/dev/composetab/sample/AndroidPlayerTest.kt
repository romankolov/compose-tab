package dev.composetab.sample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AndroidPlayerTest {
    @Test fun nativeMidiClockPauseSeekStopAndRelease() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Keep an activity foreground for audio-focus policies on newer Android.
        ActivityScenario.launch(MainActivity::class.java).use {
            val services = AndroidSampleServices(context) { null }
            val player = services.transport
            try {
                player.load(services.initialSong())
                assertTrue("Emulator must prepare native MIDI audio: ${player.status.value.message}", player.status.value.audioAvailable)
                player.play()
                withTimeout(10_000) { while (player.status.value.quarter <= .1) delay(50) }
                assertTrue(player.status.value.playing)
                player.pause(); delay(150)
                val q = player.status.value.quarter
                delay(200)
                assertEquals(q, player.status.value.quarter, .04)
                player.seek(8.0)
                withTimeout(5_000) { while (abs(player.status.value.quarter - 8) > .2) delay(50) }
                assertFalse(player.status.value.playing)
                player.play(); delay(300)
                assertTrue(player.status.value.quarter > 8)
                player.stop()
                withTimeout(5_000) { while (player.status.value.quarter > .1 || player.status.value.playing) delay(50) }
            } finally { player.close() }
        }
    }

    @Test fun realSafFileImportAndPlaybackUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            val load = device.findObject(UiSelector().text("Load GP file"))
            assertTrue("Load button must be accessible", load.waitForExists(15_000))
            // Initial decode prepares the native player before enabling Load.
            val deadline = System.currentTimeMillis() + 15_000
            while (!load.isEnabled && System.currentTimeMillis() < deadline) Thread.sleep(100)
            load.click()
            val roots = device.findObject(UiSelector().descriptionContains("Show roots"))
            if (roots.waitForExists(5_000)) roots.click()
            val downloads = device.findObject(UiSelector().text("Downloads"))
            assertTrue("System document picker must expose Downloads", downloads.waitForExists(5_000))
            downloads.click()
            val file = device.findObject(UiSelector().textContains("original-study"))
            assertTrue("CI fixture must be visible in SAF", file.waitForExists(5_000))
            file.click()
            val source = device.findObject(UiSelector().textContains("original-study.gp4"))
            assertTrue("Imported file must replace the bundled source", source.waitForExists(15_000))
            val play = device.findObject(UiSelector().text("Play"))
            assertTrue(play.waitForExists(10_000)); play.click()
            assertTrue(device.findObject(UiSelector().text("Pause")).waitForExists(5_000))
            Thread.sleep(1200)
            val image = File(context.getExternalFilesDir(null), "android-playing.png")
            assertTrue(device.takeScreenshot(image))
            // Android onStop pauses, rather than continuing sound behind another app.
            device.pressHome()
            device.waitForIdle()
        }
    }
}
