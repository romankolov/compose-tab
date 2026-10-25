package dev.composetab.sample

import androidx.compose.runtime.LaunchedEffect
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
    val view = args.firstOrNull { it.startsWith("--view=") }?.substringAfter('=') ?: "both"
    Window(onCloseRequest = ::exitApplication, title = "Compose Tab · Score Studio",
        state = rememberWindowState(width = 1120.dp, height = 800.dp)) {
        App(initialView = view)
        if (screenshot != null) LaunchedEffect(screenshot) {
            delay(4000) // Let font loading, first layout and spring animations settle.
            val file = File(screenshot)
            file.parentFile?.mkdirs()
            val bounds = window.bounds
            ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
            println("Screenshot saved: ${file.absolutePath}")
            exitApplication()
        }
    }
}
