package dev.composetab.sample
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Compose Tab · Score Studio", state = rememberWindowState(width = 1120.dp, height = 800.dp)) { App() }
}
