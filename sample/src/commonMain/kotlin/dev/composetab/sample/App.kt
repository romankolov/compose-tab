package dev.composetab.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.composetab.model.ScoreData
import dev.composetab.ui.*

private val Green = Color(0xFF347B60)
private val Ink = Color(0xFF26352F)

@Composable
fun App(initialView: String = "both") {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Color(0xFFF3F4EF), surface = Color(0xFFFFFEFA), onSurface = Ink)) {
        var data by remember { mutableStateOf<ScoreData?>(null) }
        LaunchedEffect(Unit) { data = MockGp5Decoder().decode(byteArrayOf()).toScoreData() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val song = data
            if (song == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else ScoreScreen(song, initialView)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreScreen(data: ScoreData, initialView: String) {
    val state = rememberScoreState(data)
    var staff by remember { mutableStateOf(initialView != "tab") }
    var tab by remember { mutableStateOf(true) }
    var selection by remember { mutableStateOf<ScoreEvent.NoteSelected?>(null) }
    LaunchedEffect(initialView) {
        if (initialView == "selected") {
            state.selectedNoteId = "m0-b2-n0"
            selection = ScoreEvent.NoteSelected("guitar", "m0", "m0-b2", "m0-b2-n0", true)
            state.zoomTo(1.15f)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("COMPOSE / TAB", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("A little room to compose.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFFE3ECE3)) {
                Text("SCORE STUDIO", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium, color = Green)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(data.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("${data.composer}  /  Study no. 01", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("E A D G B E · Standard") })
            AssistChip(onClick = {}, label = { Text("4/4 · Andante") })
            FilterChip(selected = staff, onClick = { if (tab) staff = !staff }, label = { Text("Notation") })
            FilterChip(selected = tab, onClick = { if (staff) tab = !tab }, label = { Text("Tablature") })
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("01   ELECTRIC GUITAR", style = MaterialTheme.typography.labelLarge, color = Green)
                        Text("Melody & expression", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text("${data.tracks.first().measures.size} measures", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
                HorizontalDivider(color = Color(0xFFE9ECE5))
                ScoreComposable(state, Modifier.fillMaxWidth().weight(1f), ScoreStyle(showStaff = staff, showTab = tab), onEvent = { event ->
                    when (event) {
                        is ScoreEvent.NoteSelected -> selection = event
                        ScoreEvent.SelectionCleared -> selection = null
                        else -> Unit
                    }
                })
                HorizontalDivider(color = Color(0xFFE9ECE5))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ZOOM", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Slider(value = state.zoom, onValueChange = state::zoomTo, valueRange = .5f..2.5f, modifier = Modifier.widthIn(max = 220.dp).weight(1f).padding(horizontal = 12.dp))
                    Text("${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { state.zoomTo(1f); state.scrollTo(0f); state.scrollVerticallyTo(0f) }) { Text("Reset") }
                }
            }
        }
        AnimatedVisibility(selection != null) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFE3ECE3), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Note selected", color = Green, fontWeight = FontWeight.Medium)
                Text(selection?.noteId.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Green)
            }
        }
        Text("Drag to explore  ·  Pinch to zoom  ·  Select a note to inspect", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
