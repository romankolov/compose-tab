package dev.composetab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CapsuleRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun capsulePaintsOnlyItsOutline() {
        compose.setContent {
            Canvas(Modifier.size(200.dp, 220.dp).testTag("capsule")) {
                drawRect(Color.White)
                drawScoreHighlight(ScoreHighlight(HighlightKind.Playhead, Rect(50f, 20f, 90f, 180f),
                    ScoreHighlightStyle(HighlightShape.Capsule, Color.Red, 3f)))
            }
        }
        val pixels = compose.onNodeWithTag("capsule").captureToImage().toPixelMap()
        assertEquals(Color.White, pixels[70, 100], "Capsule interior must remain hollow")
        assertTrue(pixels[50, 100].red > .9f && pixels[50, 100].green < .1f)
    }
}
