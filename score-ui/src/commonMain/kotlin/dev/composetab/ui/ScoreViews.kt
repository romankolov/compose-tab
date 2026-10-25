package dev.composetab.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun HorizontalScore(
    state: ScoreState, modifier: Modifier = Modifier, style: ScoreStyle = ScoreStyle(),
    onEvent: (ScoreEvent) -> Unit = {},
    onDrawHighlight: (DrawScope.(ScoreHighlight) -> Unit)? = null,
) = ScoreComposable(state, modifier, style.copy(layoutMode = ScoreLayoutMode.Horizontal), onEvent, onDrawHighlight = onDrawHighlight)

@Composable
fun VerticalScore(
    state: ScoreState, modifier: Modifier = Modifier, style: ScoreStyle = ScoreStyle(),
    onEvent: (ScoreEvent) -> Unit = {},
    onDrawHighlight: (DrawScope.(ScoreHighlight) -> Unit)? = null,
) = ScoreComposable(state, modifier, style.copy(layoutMode = ScoreLayoutMode.Vertical), onEvent, onDrawHighlight = onDrawHighlight)
