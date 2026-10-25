package dev.composetab.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.composetab.model.*
import dev.composetab.ui.*
import dev.composetab.sample.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Green = Color(0xFF347B60)
private val Ink = Color(0xFF26352F)

@OptIn(ExperimentalResourceApi::class, ExperimentalLayoutApi::class)
@Composable
fun App(initialView: String = "both", services: SampleServices? = null, onLoaded: (Result<Unit>) -> Unit = {}) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, primaryContainer = Color(0xFFE3ECE3), secondaryContainer = Color(0xFFE3ECE3), onSecondaryContainer = Ink, background = Color(0xFFF3F4EF), surface = Color(0xFFFFFEFA), onSurface = Ink)) {
        var song by remember { mutableStateOf<SampleSong?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        val currentLoaded by rememberUpdatedState(onLoaded)
        suspend fun install(value: SampleSong) {
            services?.transport?.load(value)
            song = value
            if (initialView == "playing") services?.transport?.play()
        }
        LaunchedEffect(services) {
            try {
                install(services?.initialSong() ?: Gp4DocumentAdapter.parse(Res.readBytes("files/original-study.json").decodeToString(), "Predecoded original GP4 study"))
                currentLoaded(Result.success(Unit))
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "Could not load score"
                currentLoaded(Result.failure(failure))
            } finally { busy = false }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 600.dp
                Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(if (compact) Modifier.fillMaxWidth() else Modifier) {
                            Text("COMPOSE / TAB", color = Green, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (!compact) Text("Decoder in. Your player in control.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        if (services != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = { scope.launch {
                                busy = true; error = null
                                try { services.importGp()?.let { install(it) } }
                                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                                finally { busy = false }
                            } }) { Text("Load GP file") }
                            Button(enabled = !busy, onClick = { scope.launch {
                                busy = true; error = null
                                try { install(services.requestedSong()) }
                                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                                finally { busy = false }
                            } }) { Text("Load requested tab") }
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { message ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    val loaded = song
                    if (loaded != null) ScoreScreen(loaded, initialView, services, Modifier.weight(1f), compact)
                    else if (!busy) Text("Load a Guitar Pro 1–5 file to begin. No Python installation is needed.")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreScreen(song: SampleSong, initialView: String, services: SampleServices?, modifier: Modifier, compact: Boolean) {
    val source = song.document.data
    var trackId by remember(song) { mutableStateOf(source.tracks.first().id) }
    val track = source.tracks.first { it.id == trackId }
    val document = remember(song, trackId) { ScoreDocument(source.copy(tracks = listOf(track)),
        ScoreTimeline(song.document.timeline.moments.filter { it.trackId == trackId })) }
    val state = rememberScoreState(document)
    val transport = services?.transport
    val status = if (transport != null) transport.status.collectAsState().value else TransportStatus()
    var staff by remember { mutableStateOf(initialView != "tab") }
    var tab by remember { mutableStateOf(true) }
    var darkPaper by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (initialView == "horizontal") ScoreLayoutMode.Horizontal else ScoreLayoutMode.Vertical) }
    var selectionShape by remember { mutableStateOf(HighlightShape.NoteHead) }
    var playingShape by remember { mutableStateOf(HighlightShape.NoteHead) }
    var cursorShape by remember { mutableStateOf(HighlightShape.VerticalLine) }
    var menu by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    LaunchedEffect(status.quarter, status.playing, document) {
        if (status.playing || status.quarter > 0) state.setPlaybackQuarter(status.quarter) else state.clearPlayback()
    }
    LaunchedEffect(initialView, document) {
        if (initialView == "selected") {
            val note = track.measures.flatMap { it.beats }.flatMap { it.notes }.getOrNull(2)
            state.selectedNoteId = note?.id
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis)
                Text("${source.composer}  ·  ${track.measures.size} measures", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Box(Modifier.widthIn(max = if (compact) 145.dp else 320.dp)) {
                OutlinedButton(onClick = { menu = true }) { Text(track.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    source.tracks.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { trackId = item.id; menu = false }) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = mode == ScoreLayoutMode.Vertical, onClick = { mode = ScoreLayoutMode.Vertical; state.scrollTo(0f); state.scrollVerticallyTo(0f) }, label = { Text("Vertical · wrap") })
            FilterChip(selected = mode == ScoreLayoutMode.Horizontal, onClick = { mode = ScoreLayoutMode.Horizontal; state.scrollTo(0f); state.scrollVerticallyTo(0f) }, label = { Text("Horizontal") })
            if (compact) FilterChip(selected = showOptions, onClick = { showOptions = !showOptions }, label = { Text("Options") })
            if (!compact || showOptions) {
                FilterChip(selected = staff, onClick = { if (tab) staff = !staff }, label = { Text("Notation") })
                FilterChip(selected = tab, onClick = { if (staff) tab = !tab }, label = { Text("Tab") })
                FilterChip(selected = darkPaper, onClick = { darkPaper = !darkPaper }, label = { Text("Night") })
                FilterChip(selected = state.followPlayback, onClick = { state.followPlayback = !state.followPlayback }, label = { Text("Follow cursor") })
            }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadowElevation = 2.dp) {
            Column {
                if (!compact || showOptions) {
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShapePicker("Selection", selectionShape, listOf(HighlightShape.NoteHead, HighlightShape.Rectangle, HighlightShape.VerticalLine, HighlightShape.BeatColumn, HighlightShape.None)) { selectionShape = it }
                        ShapePicker("Playing", playingShape, listOf(HighlightShape.NoteHead, HighlightShape.Rectangle, HighlightShape.VerticalLine, HighlightShape.BeatColumn, HighlightShape.None)) { playingShape = it }
                        ShapePicker("Cursor", cursorShape, listOf(HighlightShape.VerticalLine, HighlightShape.BeatColumn, HighlightShape.None)) { cursorShape = it }
                    }
                    HorizontalDivider(color = Color(0xFFE9ECE5))
                }
                ScoreComposable(state, Modifier.fillMaxWidth().weight(1f), ScoreStyle(
                    showStaff = staff, showTab = tab, layoutMode = mode,
                    paper = if (darkPaper) Color(0xFF202C29) else Color(0xFFFFFEFA),
                    ink = if (darkPaper) Color(0xFFE9F0E6) else Ink,
                    muted = if (darkPaper) Color(0xFF9CAEA1) else Color(0xFF89938B),
                    accent = if (darkPaper) Color(0xFF8AD5AB) else Green,
                    selectedHighlight = ScoreHighlightStyle(selectionShape, Color(0x664BA577)),
                    playingHighlight = ScoreHighlightStyle(playingShape, Color(0x663E9DD0)),
                    playhead = ScoreHighlightStyle(cursorShape, Color(0xFF2D8FBF), 2f),
                ), onEvent = { event ->
                    if (event is ScoreEvent.NoteSelected) {
                        val moment = document.timeline.moments.filter { it.beatId == event.beatId }.minByOrNull { abs(it.startQuarter - status.quarter) }
                        moment?.let { transport?.seek(it.startQuarter) }
                    }
                })
                HorizontalDivider(color = Color(0xFFE9ECE5))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Zoom", style = MaterialTheme.typography.labelSmall)
                    Slider(state.zoom, state::zoomTo, valueRange = .5f..2.5f, modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp).padding(horizontal = 12.dp))
                    Text("${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { state.zoomTo(1f); state.scrollTo(0f); state.scrollVerticallyTo(0f) }) { Text("Reset") }
                }
            }
        }
        if (transport != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (status.playing) transport.pause() else transport.play() }) { Text(if (status.playing) "Pause" else "Play") }
                OutlinedButton(onClick = transport::stop) { Text("Stop") }
                Slider(status.quarter.toFloat().coerceIn(0f, status.durationQuarters.toFloat().coerceAtLeast(1f)), { transport.seek(it.toDouble()) },
                    valueRange = 0f..status.durationQuarters.toFloat().coerceAtLeast(1f), modifier = Modifier.weight(1f))
                Text("${status.quarter.toInt()} / ${status.durationQuarters.toInt()} q", style = MaterialTheme.typography.labelSmall)
            }
            Text(status.message, style = MaterialTheme.typography.bodySmall, color = if (status.audioAvailable) Green else Color.Gray)
        } else Text("Android and Desktop provide GP1–GP5 import and General MIDI playback. This platform shows the predecoded original study.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        AnimatedVisibility(state.selectedNoteId != null) {
            val note = track.measures.flatMap { it.beats }.flatMap { it.notes }.firstOrNull { it.id == state.selectedNoteId }
            Text(note?.let { "Selected · MIDI ${it.midi} · String ${it.string} · Fret ${it.fret}" }.orEmpty(),
                Modifier.fillMaxWidth().background(Color(0xFFE3ECE3), RoundedCornerShape(10.dp)).padding(10.dp), color = Green)
        }
        Text("${song.source}  ·  ${song.warnings.firstOrNull().orEmpty()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 2)
    }
}

@Composable
private fun ShapePicker(label: String, selected: HighlightShape, choices: List<HighlightShape>, onChange: (HighlightShape) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label: ${selected.name}", style = MaterialTheme.typography.labelSmall) }
        DropdownMenu(open, { open = false }) {
            choices.forEach { shape -> DropdownMenuItem(text = { Text(shape.name) }, onClick = { onChange(shape); open = false }) }
        }
    }
}
