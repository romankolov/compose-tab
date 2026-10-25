package dev.composetab.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import dev.composetab.model.*

sealed interface ScoreEvent {
    data class NoteSelected(val trackId: String, val measureId: String, val beatId: String, val noteId: String, val tablature: Boolean) : ScoreEvent
    data object SelectionCleared : ScoreEvent
    data class ViewportChanged(val zoom: Float, val scroll: Float, val verticalScroll: Float = 0f) : ScoreEvent
}

enum class ScoreLayoutMode { Horizontal, Vertical }
enum class HighlightShape { None, NoteHead, Rectangle, VerticalLine, BeatColumn }
enum class HighlightKind { Selection, Playing, Playhead }

/** Widths/padding are logical score-space dp, and scale with zoom. */
data class ScoreHighlightStyle(
    val shape: HighlightShape = HighlightShape.NoteHead,
    val color: Color = Color(0x55347B60),
    val lineWidth: Float = 2f,
    val padding: Float = 3f,
) {
    init { require(lineWidth.isFinite() && lineWidth > 0); require(padding.isFinite() && padding >= 0) }
}

@Stable
class ScoreState(initialData: ScoreData) {
    var document by mutableStateOf(ScoreDocument(initialData))
        private set
    var data: ScoreData
        get() = document.data
        set(value) { if (value != document.data) submit(ScoreDocument(value)) }
    var selectedNoteIds by mutableStateOf<Set<String>>(emptySet())
    /** Source-compatible single-selection shorthand. Setting it replaces the selection. */
    var selectedNoteId: String?
        get() = selectedNoteIds.firstOrNull()
        set(value) { selectedNoteIds = value?.let { setOf(it) } ?: emptySet() }
    var playback by mutableStateOf<ScorePlaybackPosition?>(null)
        private set
    var followPlayback by mutableStateOf(true)
    internal var revealRequest by mutableStateOf<String?>(null)
    var zoom by mutableFloatStateOf(1f)
        private set
    var scroll by mutableFloatStateOf(0f)
        private set
    var verticalScroll by mutableFloatStateOf(0f)
        private set

    constructor(initialDocument: ScoreDocument) : this(initialDocument.data) { document = initialDocument }

    /** Publish replacement values on the UI thread. A replacement prunes stale IDs. */
    fun submit(value: ScoreDocument, resetViewport: Boolean = false) {
        if (document === value) return
        val ids = value.data.tracks.flatMap { it.measures }.flatMap { it.beats }.flatMap { it.notes }.map { it.id }.toSet()
        selectedNoteIds = selectedNoteIds.intersect(ids)
        playback = null
        revealRequest = null
        document = value
        if (resetViewport) { scrollTo(0f); scrollVerticallyTo(0f) }
    }
    fun <T> submit(source: T, adapter: ScoreAdapter<T>, resetViewport: Boolean = false) = submit(adapter.adapt(source), resetViewport)
    fun selectNotes(vararg ids: String) { selectedNoteIds = ids.toSet() }
    /** Host clock drives this; the library never starts a timer or audio engine. */
    fun setPlaybackPosition(value: ScorePlaybackPosition?) { playback = value }
    fun setPlaybackQuarter(quarter: Double) { playback = document.timeline.at(quarter) }
    fun setPlayingNotes(vararg ids: String) { playback = ScorePlaybackPosition(noteIds = ids.toSet()) }
    fun setPlayhead(beatId: String, fraction: Float = 0f) { playback = ScorePlaybackPosition(listOf(ScorePosition(beatId, fraction)), playback?.noteIds.orEmpty()) }
    fun clearPlayback() { playback = null }
    fun revealNote(noteId: String) { revealRequest = noteId }
    fun revealBeat(beatId: String) { revealRequest = beatId }
    fun scrollVerticallyTo(value: Float) { verticalScroll = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f }
    fun zoomTo(value: Float) { zoom = value.takeIf { it.isFinite() }?.coerceIn(.5f, 2.5f) ?: 1f }
    fun scrollTo(value: Float) { scroll = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f }
}

@Composable
fun rememberScoreState(): ScoreState = remember { ScoreState(ScoreData("Untitled", tracks = emptyList())) }

@Composable
fun rememberScoreState(data: ScoreData): ScoreState {
    val state = remember { ScoreState(data) }
    SideEffect { state.data = data }
    return state
}

@Composable
fun rememberScoreState(document: ScoreDocument): ScoreState {
    val state = remember { ScoreState(document) }
    SideEffect { if (state.document !== document) state.submit(document) }
    return state
}

data class ScoreStyle(
    val paper: Color = Color(0xFFFFFEFA),
    val ink: Color = Color(0xFF26352F),
    val muted: Color = Color(0xFF89938B),
    val accent: Color = Color(0xFF347B60),
    val selection: Color = Color(0x33347B60),
    val measureWidth: Float = 280f,
    val lineSpacing: Float = 10f,
    val trackSpacing: Float = 270f,
    val showStaff: Boolean = true,
    val showTab: Boolean = true,
    val showTabRhythm: Boolean = true,
    val layoutMode: ScoreLayoutMode = ScoreLayoutMode.Horizontal,
    val selectedHighlight: ScoreHighlightStyle = ScoreHighlightStyle(color = selection),
    val playingHighlight: ScoreHighlightStyle = ScoreHighlightStyle(color = Color(0x6675B4D7)),
    val playhead: ScoreHighlightStyle = ScoreHighlightStyle(HighlightShape.VerticalLine, Color(0xFF2482B0)),
) {
    init { require(measureWidth.isFinite() && measureWidth >= 140f); require(lineSpacing.isFinite() && lineSpacing >= 6f); require(trackSpacing.isFinite() && trackSpacing >= 220f) }
}
