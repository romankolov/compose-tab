package dev.composetab.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** The score owns the screen. Import, track selection and customization live in More. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun App(initialView: String = "both", services: SampleServices? = null, onLoaded: (Result<Unit>) -> Unit = {}) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green, primaryContainer = Color(0xFFE3ECE3), secondaryContainer = Color(0xFFE3ECE3), onSecondaryContainer = Ink, surfaceTint = Green, background = Color(0xFFFFFEFA), surface = Color(0xFFFFFEFA), onSurface = Ink)) {
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
                install(services?.initialSong() ?: Gp4DocumentAdapter.parse(Res.readBytes("files/original-study.json").decodeToString(), "Predecoded original study"))
                currentLoaded(Result.success(Unit))
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: "Could not load score"
                currentLoaded(Result.failure(failure))
            } finally { busy = false }
        }
        fun load(requested: Boolean) {
            if (services == null || busy) return
            scope.launch {
                busy = true; error = null
                try {
                    val value = if (requested) services.requestedSong() else services.importGp()
                    value?.let { install(it) }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = failure.message ?: "Could not open file"
                } finally { busy = false }
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Studio(song, initialView, services, busy, onImport = { load(false) }, onDownload = { load(true) })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                error?.let { message ->
                    Snackbar(Modifier.align(Alignment.TopCenter).padding(12.dp), action = {
                        TextButton(onClick = { error = null }) { Text("Dismiss") }
                    }) { Text(message) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Studio(song: SampleSong?, initialView: String, services: SampleServices?, busy: Boolean, onImport: () -> Unit, onDownload: () -> Unit) {
    val source = song?.document?.data ?: ScoreData("Open a Guitar Pro file", tracks = emptyList())
    var trackId by remember(song) { mutableStateOf(source.tracks.firstOrNull()?.id) }
    val track = source.tracks.firstOrNull { it.id == trackId }
    val document = remember(song, trackId) {
        if (song != null && track != null) ScoreDocument(source.copy(tracks = listOf(track)),
            ScoreTimeline(song.document.timeline.moments.filter { it.trackId == trackId })) else ScoreDocument(source)
    }
    val state = rememberScoreState(document)
    val transport = services?.transport
    val status = transport?.status?.collectAsState()?.value ?: TransportStatus()
    var staff by remember { mutableStateOf(initialView != "tab") }
    var tab by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(false) }
    var tabSpacing by remember { mutableStateOf(18f) }
    var tabSystemGap by remember { mutableStateOf(52f) }
    var rhythmStyle by remember { mutableStateOf(TabRhythmStyle.Beams) }
    var tabRhythm by remember { mutableStateOf(true) }
    var legatoLabels by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (initialView == "horizontal") ScoreLayoutMode.Horizontal else ScoreLayoutMode.Vertical) }
    var selectionShape by remember { mutableStateOf(HighlightShape.NoteHead) }
    var playingShape by remember { mutableStateOf(HighlightShape.NoteHead) }
    var cursorShape by remember { mutableStateOf(HighlightShape.VerticalLine) }
    var more by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var scrub by remember(song) { mutableStateOf<Float?>(null) }
    val clock = remember(song) { song?.let { runCatching { MidiTempoMap.parse(it.midi) }.getOrNull() } }
    fun time(quarter: Double): String {
        val seconds = ((clock?.millisecondsAt(quarter) ?: 0.0) / 1000).toInt().coerceAtLeast(0)
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
    LaunchedEffect(status.quarter, status.playing, document) {
        if (status.playing || status.quarter > 0) state.setPlaybackQuarter(status.quarter) else state.clearPlayback()
    }
    LaunchedEffect(initialView, document) {
        if (initialView == "selected") state.selectedNoteId = track?.measures?.flatMap { it.beats }?.flatMap { it.notes }?.getOrNull(2)?.id
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (song != null) ScoreComposable(state, Modifier.fillMaxSize(), ScoreStyle(
                showStaff = staff, showTab = tab, layoutMode = mode,
                tabStringSpacing = tabSpacing, tabSystemSpacing = tabSystemGap,
                tabRhythmStyle = rhythmStyle, showTabRhythm = tabRhythm, showLegatoLabels = legatoLabels,
                paper = if (dark) Color(0xFF202C29) else Color(0xFFFFFEFA),
                ink = if (dark) Color(0xFFE9F0E6) else Ink,
                muted = if (dark) Color(0xFF9CAEA1) else Color(0xFF89938B),
                accent = if (dark) Color(0xFF8AD5AB) else Green,
                selectedHighlight = ScoreHighlightStyle(selectionShape, Color(0x664BA577)),
                playingHighlight = ScoreHighlightStyle(playingShape, Color(0x663E9DD0)),
                playhead = ScoreHighlightStyle(cursorShape, Color(0xFF2D8FBF), 2f),
            ), onEvent = { event ->
                if (event is ScoreEvent.NoteSelected) document.timeline.moments.filter { it.beatId == event.beatId }
                    .minByOrNull { abs(it.startQuarter - status.quarter) }?.let { transport?.seek(it.startQuarter) }
            }) else Text(if (busy) "Opening score…" else "Use More to open a Guitar Pro file", Modifier.align(Alignment.Center))
        }
        Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Text(track?.name ?: "Compose Tab", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Green)
                    }
                    if (transport != null) {
                        Button(enabled = song != null && !busy, onClick = { if (status.playing) transport.pause() else transport.play() }) { Text(if (status.playing) "Pause" else "Play") }
                        TextButton(enabled = song != null, onClick = transport::stop) { Text("Stop") }
                    }
                    Box {
                        TextButton(onClick = { more = true }) { Text("More") }
                        DropdownMenu(more, { more = false }) {
                            if (services != null) {
                                DropdownMenuItem(text = { Text("Load GP file") }, enabled = !busy, onClick = { more = false; onImport() })
                                DropdownMenuItem(text = { Text("Load requested tab") }, enabled = !busy, onClick = { more = false; onDownload() })
                                HorizontalDivider()
                            }
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { more = false; settings = true })
                            DropdownMenuItem(text = { Text(if (state.followPlayback) "Disable follow cursor" else "Follow cursor") }, onClick = { more = false; state.followPlayback = !state.followPlayback })
                        }
                    }
                }
                if (transport != null && song != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time((scrub?.toDouble() ?: status.quarter)), style = MaterialTheme.typography.labelSmall)
                    Slider(value = (scrub ?: status.quarter.toFloat()).coerceIn(0f, status.durationQuarters.toFloat().coerceAtLeast(1f)),
                        onValueChange = { scrub = it }, onValueChangeFinished = { scrub?.let { transport.seek(it.toDouble()) }; scrub = null },
                        valueRange = 0f..status.durationQuarters.toFloat().coerceAtLeast(1f), modifier = Modifier.weight(1f))
                    Text(time(status.durationQuarters), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Score settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { settings = false }) { Text("Done") }
            }
            Text(song?.source.orEmpty(), style = MaterialTheme.typography.bodySmall)
            Text(source.composer, style = MaterialTheme.typography.bodySmall)
            Text("Track", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { source.tracks.forEach { item ->
                FilterChip(selected = trackId == item.id, onClick = { trackId = item.id }, label = { Text(item.name) })
            } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == ScoreLayoutMode.Vertical, { mode = ScoreLayoutMode.Vertical; state.scrollTo(0f); state.scrollVerticallyTo(0f) }, label = { Text("Vertical · wrap") })
                FilterChip(mode == ScoreLayoutMode.Horizontal, { mode = ScoreLayoutMode.Horizontal; state.scrollTo(0f); state.scrollVerticallyTo(0f) }, label = { Text("Horizontal") })
                FilterChip(staff, { if (tab) staff = !staff }, label = { Text("Notation") })
                FilterChip(tab, { if (staff) tab = !tab }, label = { Text("Tab") })
                FilterChip(dark, { dark = !dark }, label = { Text("Night") })
                FilterChip(state.followPlayback, { state.followPlayback = !state.followPlayback }, label = { Text("Follow cursor") })
            }
            Text("Tab string spacing · ${tabSpacing.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(tabSpacing, { tabSpacing = it }, valueRange = 12f..32f)
            Text("Tab system gap · ${tabSystemGap.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(tabSystemGap, { tabSystemGap = it }, valueRange = 24f..100f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(rhythmStyle == TabRhythmStyle.Beams, { rhythmStyle = TabRhythmStyle.Beams }, label = { Text("Rhythm beams") })
                FilterChip(rhythmStyle == TabRhythmStyle.Notes, { rhythmStyle = TabRhythmStyle.Notes }, label = { Text("Rhythm note heads") })
                FilterChip(tabRhythm, { tabRhythm = !tabRhythm }, label = { Text("Show tab rhythm") })
                FilterChip(legatoLabels, { legatoLabels = !legatoLabels }, label = { Text("H/P labels") })
            }
            Text("Zoom · ${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(state.zoom, state::zoomTo, valueRange = .5f..2.5f)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapePicker("Selection", selectionShape) { selectionShape = it }
                ShapePicker("Playing", playingShape) { playingShape = it }
                ShapePicker("Cursor", cursorShape) { cursorShape = it }
            }
            TextButton(onClick = { state.zoomTo(1f); state.scrollTo(0f); state.scrollVerticallyTo(0f) }) { Text("Reset viewport") }
            HorizontalDivider()
            Text(if (transport != null) status.message else "UI-only preview on this platform", style = MaterialTheme.typography.bodySmall)
            song?.warnings?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ShapePicker(label: String, selected: HighlightShape, onChange: (HighlightShape) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${selected.name}", style = MaterialTheme.typography.labelSmall) }
        DropdownMenu(open, { open = false }) {
            HighlightShape.entries.forEach { shape -> DropdownMenuItem(text = { Text(shape.name) }, onClick = { onChange(shape); open = false }) }
        }
    }
}
