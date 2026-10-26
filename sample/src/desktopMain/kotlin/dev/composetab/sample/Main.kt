package dev.composetab.sample

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.composetab.ui.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/** CI uses the same real desktop window as an interactive user, not a mock image. */
fun main(args: Array<String>) = application {
    val screenshot = args.firstOrNull { it.startsWith("--screenshot=") }?.substringAfter('=')
    val input = args.firstOrNull { it.startsWith("--gp4=") }?.substringAfter('=')
    val services = remember { DesktopSampleServices(input) }
    var loaded by remember { mutableStateOf<Result<Unit>?>(null) }
    DisposableEffect(services) { onDispose { services.transport.close() } }
    val view = args.firstOrNull { it.startsWith("--view=") }?.substringAfter('=') ?: "both"
    Window(onCloseRequest = ::exitApplication, title = "Compose Tab · Score Studio",
        state = rememberWindowState(width = 1120.dp, height = 800.dp)) {
        if ("--ornaments" in args) {
            val state = remember { ScoreState(ornamentStudy()).apply { followPlayback = false } }
            ScoreComposable(state, Modifier.fillMaxSize(), ScoreStyle(showStaff = view != "tab", layoutMode = ScoreLayoutMode.Vertical))
            LaunchedEffect(Unit) { loaded = Result.success(Unit) }
        } else App(initialView = view, services = services, onLoaded = { loaded = it })
        if (screenshot != null && loaded != null) LaunchedEffect(screenshot, loaded) {
            loaded?.exceptionOrNull()?.let { System.err.println(it.stackTraceToString()); kotlin.system.exitProcess(2) }
            delay(1800) // Let font loading, first layout and spring animations settle.
            if (view == "playing" && services.transport.status.value.quarter <= 0) kotlin.system.exitProcess(3)
            val file = File(screenshot)
            file.parentFile?.mkdirs()
            val bounds = window.bounds
            ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
            println("Screenshot saved: ${file.absolutePath}")
            exitApplication()
        }
    }
}
