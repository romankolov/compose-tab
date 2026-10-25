package dev.composetab.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import dev.composetab.model.ScoreData

sealed interface ScoreEvent {
    data class NoteSelected(val trackId: String, val measureId: String, val beatId: String, val noteId: String, val tablature: Boolean) : ScoreEvent
    data object SelectionCleared : ScoreEvent
    data class ViewportChanged(val zoom: Float, val scroll: Float, val verticalScroll: Float = 0f) : ScoreEvent
}

@Stable
class ScoreState(initialData: ScoreData) {
    var data by mutableStateOf(initialData)
    var selectedNoteId by mutableStateOf<String?>(null)
    var zoom by mutableFloatStateOf(1f)
        private set
    var scroll by mutableFloatStateOf(0f)
        private set
    var verticalScroll by mutableFloatStateOf(0f)
        private set
    fun scrollVerticallyTo(value: Float) { verticalScroll = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f }
    fun zoomTo(value: Float) { zoom = value.takeIf { it.isFinite() }?.coerceIn(.5f, 2.5f) ?: 1f }
    fun scrollTo(value: Float) { scroll = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f }
}

@Composable
fun rememberScoreState(data: ScoreData): ScoreState {
    val state = remember { ScoreState(data) }
    SideEffect { state.data = data }
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
) {
    init { require(measureWidth.isFinite() && measureWidth >= 140f); require(lineSpacing.isFinite() && lineSpacing >= 6f); require(trackSpacing.isFinite() && trackSpacing >= 220f) }
}
