package dev.composetab.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.composetab.model.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class TabEffectsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun conventionalGhostAndHarmonicFretLabels() {
        val note = Note("n", 64, 1, 12, 0)
        assertEquals("12", tabFretLabel(note))
        assertEquals("(12)", tabFretLabel(note.copy(effects = listOf(Effect.Ghost))))
        assertEquals("<12>", tabFretLabel(note.copy(effects = listOf(Effect.Harmonic()))))
        assertEquals("(<12>)", tabFretLabel(note.copy(effects = listOf(Effect.Ghost, Effect.Harmonic(HarmonicKind.Pinch)))))
    }

    @Test fun articulationsAnchorToTheirOwnTabStrings() {
        val effects = listOf(Effect.HammerOn, Effect.PullOff, Effect.Bend(2f), Effect.Ghost, Effect.Harmonic())
        val notes = effects.mapIndexed { i, e -> Note("n$i", 64 - i, i + 1, i + 2, 0, effects = listOf(e)) }
        val data = ScoreData("Effects", tracks = listOf(Track("t", "Guitar", measures = listOf(
            Measure("m", 1, beats = listOf(Beat("b", notes = notes, strum = StrumDirection.Down),
                Beat("next", notes = notes.map { it.copy(id = it.id + "next", fret = it.fret + 2, effects = emptyList()) }, strum = StrumDirection.Up)))
        ))))
        val style = ScoreStyle(showStaff = false, showTab = true)
        val placed = layoutScore(data, style).measures.single()
        val anchors = mutableMapOf<Effect, Offset>()
        compose.setContent {
            ScoreComposable(ScoreState(data).apply { followPlayback = false }, Modifier.size(700.dp, 500.dp).testTag("effects"), style,
                onDrawEffect = { effect, anchor, _ -> anchors[effect] = anchor })
        }
        compose.onNodeWithTag("effects").captureToImage()
        compose.runOnIdle {
            effects.forEachIndexed { i, effect ->
                assertEquals(placed.y + placed.tabOffset + i * style.tabStringSpacing - 12, anchors.getValue(effect).y, .1f)
            }
        }
    }

    @Test fun playbackFollowScrollUsesIntermediateFrames() {
        val data = ScoreData("Follow", tracks = listOf(Track("t", "Guitar", measures = (0..11).map { i ->
            Measure("m$i", i + 1, beats = listOf(Beat("b$i", notes = listOf(Note("n$i", 64, 1, 0, 0)))))
        })))
        val state = ScoreState(data).apply { followPlayback = false }
        compose.mainClock.autoAdvance = false
        compose.setContent { ScoreComposable(state, Modifier.size(600.dp, 320.dp)) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { state.setPlayhead("b11"); state.followPlayback = true }
        compose.mainClock.advanceTimeBy(96)
        var intermediate = 0f
        compose.runOnIdle { intermediate = state.scroll; assertTrue(intermediate > 0f) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(state.scroll > intermediate, "Follow must animate, not jump to its final offset") }
    }
}
