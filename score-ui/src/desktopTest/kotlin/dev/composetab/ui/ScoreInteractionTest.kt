package dev.composetab.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.composetab.model.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

/** Real Compose scene: these tests run under Xvfb in GitHub Actions. */
@OptIn(ExperimentalTestApi::class)
class ScoreInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val data = ScoreData("Interaction test", tracks = listOf(Track("track", "Guitar", measures = listOf(
        Measure("measure", 1, beats = listOf(
            Beat("a", notes = listOf(Note("first", 64, 1, 0, 0))),
            Beat("b", notes = listOf(Note("second", 65, 1, 1, 1))),
        ))
    ))))

    @Test fun clickTabThenNavigateAndClearWithKeyboard() {
        val state = ScoreState(data)
        val events = mutableListOf<ScoreEvent>()
        val style = ScoreStyle()
        val target = layoutScore(data, style).targets.first { it.event.tablature }.bounds.center
        compose.setContent { ScoreComposable(state, Modifier.size(600.dp, 320.dp).testTag("score"), style, events::add) }
        compose.onNodeWithTag("score").performTouchInput { click(Offset(target.x.dp.toPx(), target.y.dp.toPx())) }
        compose.runOnIdle {
            assertEquals("first", state.selectedNoteId)
            assertTrue(events.filterIsInstance<ScoreEvent.NoteSelected>().last().tablature)
        }
        compose.onNodeWithTag("score").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals("second", state.selectedNoteId) }
        compose.onNodeWithTag("score").performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle {
            assertNull(state.selectedNoteId)
            assertEquals(ScoreEvent.SelectionCleared, events.last())
        }
    }

    @Test fun staffHitAndEmptyClickEmitDistinctEvents() {
        val state = ScoreState(data)
        val events = mutableListOf<ScoreEvent>()
        val style = ScoreStyle()
        val target = layoutScore(data, style).targets.first { !it.event.tablature }.bounds.center
        compose.setContent { ScoreComposable(state, Modifier.size(600.dp, 320.dp).testTag("score"), style, events::add) }
        compose.onNodeWithTag("score").performTouchInput { click(Offset(target.x.dp.toPx(), target.y.dp.toPx())) }
        compose.runOnIdle { assertFalse(events.filterIsInstance<ScoreEvent.NoteSelected>().last().tablature) }
        compose.onNodeWithTag("score").performTouchInput { click(Offset(580.dp.toPx(), 300.dp.toPx())) }
        compose.runOnIdle { assertEquals(ScoreEvent.SelectionCleared, events.last()) }
    }
}
