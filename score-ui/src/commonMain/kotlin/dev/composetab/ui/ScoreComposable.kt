package dev.composetab.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.composetab.model.*

/** Horizontal continuous score. Drag to pan; pinch to zoom; click a note or fret to select.
 * onDrawEffect is an additive extension hook in score-space coordinates (logical dp).
 * No decoding, scheduling or audio dependencies belong in this composable. */
@Composable
fun ScoreComposable(
    state: ScoreState,
    modifier: Modifier = Modifier,
    style: ScoreStyle = ScoreStyle(),
    onEvent: (ScoreEvent) -> Unit = {},
    onDrawEffect: (DrawScope.(Effect, Offset, ScoreStyle) -> Unit)? = null,
) {
    val layout = remember(state.data, style) { layoutScore(state.data, style) }
    val zoom by animateFloatAsState(state.zoom, spring(stiffness = 500f), label = "score zoom")
    val text = rememberTextMeasurer(cacheSize = 256)
    val currentEvent by rememberUpdatedState(onEvent)
    val focus = remember { FocusRequester() }
    fun viewportEvent() = currentEvent(ScoreEvent.ViewportChanged(state.zoom, state.scroll, state.verticalScroll))
    fun navigate(forward: Boolean): Boolean {
        val targets = layout.targets.filter { it.event.tablature == style.showTab }
        if (targets.isEmpty()) return false
        val index = targets.indexOfFirst { it.event.noteId == state.selectedNoteId }
        val next = if (index < 0) { if (forward) 0 else targets.lastIndex }
            else (index + if (forward) 1 else -1).coerceIn(0, targets.lastIndex)
        val target = targets[next]
        state.selectedNoteId = target.event.noteId
        state.scrollTo((target.bounds.center.x - 100f).coerceAtLeast(0f))
        state.scrollVerticallyTo((target.bounds.center.y - 140f).coerceAtLeast(0f))
        currentEvent(target.event)
        viewportEvent()
        return true
    }
    Canvas(modifier
        .focusRequester(focus)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionRight -> navigate(true)
                Key.DirectionLeft -> navigate(false)
                Key.Escape -> { state.selectedNoteId = null; currentEvent(ScoreEvent.SelectionCleared); true }
                Key.Equals -> { state.zoomTo(state.zoom + .1f); viewportEvent(); true }
                Key.Minus -> { state.zoomTo(state.zoom - .1f); viewportEvent(); true }
                else -> false
            }
        }
        .focusable()
        .semantics {
            contentDescription = "${state.data.title}, music score, ${state.data.tracks.size} tracks"
            stateDescription = state.selectedNoteId?.let { "Selected note $it" } ?: "No note selected"
            customActions = listOf(
                CustomAccessibilityAction("Next note") { navigate(true) },
                CustomAccessibilityAction("Previous note") { navigate(false) },
                CustomAccessibilityAction("Zoom in") { state.zoomTo(state.zoom + .1f); viewportEvent(); true },
                CustomAccessibilityAction("Zoom out") { state.zoomTo(state.zoom - .1f); viewportEvent(); true },
            )
        }
        .pointerInput(state, layout) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.fold(Offset.Zero) { acc, change -> acc + change.scrollDelta }
                        val maxX = (layout.width - size.width / (density * state.zoom)).coerceAtLeast(0f)
                        val maxY = (layout.height - size.height / (density * state.zoom)).coerceAtLeast(0f)
                        if (event.keyboardModifiers.isCtrlPressed) state.zoomTo(state.zoom - delta.y * .08f)
                        else if (layout.rows.size <= 1 && maxX > 0f) state.scrollTo((state.scroll + (delta.x + delta.y) * 32 / state.zoom).coerceIn(0f, maxX))
                        else {
                            state.scrollTo((state.scroll + delta.x * 32 / state.zoom).coerceIn(0f, maxX))
                            state.scrollVerticallyTo((state.verticalScroll + delta.y * 32 / state.zoom).coerceIn(0f, maxY))
                        }
                        event.changes.forEach { it.consume() }
                        viewportEvent()
                    }
                }
            }
        }
        .pointerInput(state, layout, zoom) {
            detectTapGestures { point ->
                focus.requestFocus()
                val scale = density * zoom
                val sx = state.scroll.coerceAtMost((layout.width - size.width / scale).coerceAtLeast(0f))
                val sy = state.verticalScroll.coerceAtMost((layout.height - size.height / scale).coerceAtLeast(0f))
                val logical = Offset(point.x / scale + sx, point.y / scale + sy)
                val hit = layout.hitTest(logical)
                state.selectedNoteId = hit?.event?.noteId
                currentEvent(hit?.event ?: ScoreEvent.SelectionCleared)
            }
        }
        .pointerInput(state, layout) {
            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                val oldZoom = state.zoom
                state.zoomTo(oldZoom * gestureZoom)
                // Keep the pinch anchor stationary while scaling.
                val anchorX = centroid.x / density
                val anchorY = centroid.y / density
                val maxX = (layout.width - size.width / (density * state.zoom)).coerceAtLeast(0f)
                val maxY = (layout.height - size.height / (density * state.zoom)).coerceAtLeast(0f)
                state.scrollTo((state.scroll + anchorX / oldZoom - anchorX / state.zoom - pan.x / (density * state.zoom)).coerceIn(0f, maxX))
                state.scrollVerticallyTo((state.verticalScroll + anchorY / oldZoom - anchorY / state.zoom - pan.y / (density * state.zoom)).coerceIn(0f, maxY))
                viewportEvent()
            }
        }) {
        drawRect(style.paper)
        val factor = density * zoom
        val scrollX = state.scroll.coerceAtMost((layout.width - size.width / factor).coerceAtLeast(0f))
        val scrollY = state.verticalScroll.coerceAtMost((layout.height - size.height / factor).coerceAtLeast(0f))
        fun DrawScope.label(value: String, x: Float, y: Float, font: Float = 12f, muted: Boolean = false) {
            // Counteract density here: the entire logical drawing is scaled by density below.
            val result = text.measure(value, TextStyle(color = if (muted) style.muted else style.ink, fontSize = (font / density).sp))
            drawText(result, topLeft = Offset(x, y))
        }
        withTransform({ scale(factor, factor, Offset.Zero); translate(-scrollX, -scrollY) }) {
            // Cull complete measures before drawing any notation or measuring text.
            layout.visibleMeasures(scrollX, scrollY, scrollX + size.width / factor, scrollY + size.height / factor).forEach { placed ->
                val (track, measure, x, y, beats) = placed
                val gap = style.lineSpacing
                val tabY = y + if (style.showStaff) 110f else 0f
                label("${measure.number}", x + 8, y - 27, 11f, true)
                if (measure == track.measures.firstOrNull()) label(track.name, x, y - 48, 12f, true)
                fun lines(top: Float, count: Int) {
                    repeat(count) { i -> drawLine(style.muted.copy(alpha = .55f), Offset(x, top + i * gap), Offset(x + placed.width, top + i * gap), .7f) }
                    drawLine(style.ink, Offset(x + placed.width, top), Offset(x + placed.width, top + (count - 1) * gap), 1f)
                }
                if (style.showStaff) {
                    lines(y, 5)
                    label("${measure.time.numerator}", x + 14, y + 1, 17f)
                    label("${measure.time.denominator}", x + 14, y + 20, 17f)
                }
                if (style.showTab) { lines(tabY, track.tuning.size); label("TAB", x + 4, tabY + 17, 10f, true) }
                if (measure.repeatStart || measure.repeatEnd) {
                    val rx = if (measure.repeatStart) x + 3 else x + placed.width - 5
                    val top = if (style.showStaff) y else tabY
                    drawLine(style.ink, Offset(rx, top), Offset(rx, top + 4 * gap), 2.5f)
                    repeat(2) { drawCircle(style.ink, 1.8f, Offset(rx + if (measure.repeatStart) 6 else -6, top + (1.5f + it) * gap)) }
                }
                beats.forEach { (beat, bx) ->
                    if (beat.isRest) {
                        if (style.showStaff) {
                            if (beat.duration == Duration.Whole || beat.duration == Duration.Half) drawRect(style.ink, Offset(bx - 4, y + 15), Size(9f, 4f))
                            else {
                                val rest = Path().apply { moveTo(bx - 2, y + 8); lineTo(bx + 3, y + 17); lineTo(bx - 3, y + 23); lineTo(bx + 3, y + 31) }
                                drawPath(rest, style.ink, style = Stroke(2.5f))
                            }
                        }
                        if (style.showTab) label("–", bx - 4, tabY + 16, 13f, true)
                    }
                    beat.notes.forEach { note ->
                        val ny = y + 4 * gap - note.staffStep * gap / 2
                        val ty = tabY + (note.string - 1) * gap
                        if (note.id == state.selectedNoteId) {
                            if (style.showStaff) drawCircle(style.selection, 13f, Offset(bx, ny))
                            if (style.showTab) drawRoundRect(style.selection, Offset(bx - 12, ty - 10), Size(24f, 20f))
                        }
                        if (style.showStaff) {
                            // Ledger lines on every second staff step outside the five-line staff.
                            if (note.staffStep < 0) for (step in -2 downTo note.staffStep step 2) drawLine(style.ink, Offset(bx - 9, y + 4 * gap - step * gap / 2), Offset(bx + 9, y + 4 * gap - step * gap / 2), .8f)
                            if (note.staffStep > 8) for (step in 10..note.staffStep step 2) drawLine(style.ink, Offset(bx - 9, y + 4 * gap - step * gap / 2), Offset(bx + 9, y + 4 * gap - step * gap / 2), .8f)
                            drawOval(style.ink, Offset(bx - 5, ny - 3.5f), Size(10f, 7f), style = if (beat.duration in listOf(Duration.Whole, Duration.Half)) Stroke(1.5f) else Fill)
                            note.accidental?.let { label(when (it) { Accidental.Sharp -> "♯"; Accidental.Flat -> "♭"; Accidental.Natural -> "♮" }, bx - 19, ny - 11, 17f) }
                            if (beat.dotted) drawCircle(style.ink, 1.6f, Offset(bx + 10, ny - 2))
                        }
                        if (style.showTab) {
                            drawRect(style.paper, Offset(bx - 8, ty - 7), Size(16f, 14f))
                            label(note.fret.toString(), bx - if (note.fret >= 10) 7 else 3.5f, ty - 8, 13f)
                        }
                        val effectY = if (style.showTab) tabY - 16 else y - 27
                        note.effects.forEach { effect ->
                            when (effect) {
                                is Effect.Bend -> {
                                    val path = Path().apply { moveTo(bx + 7, effectY + 9); quadraticBezierTo(bx + 24, effectY + 9, bx + 24, effectY - 8); lineTo(bx + 20, effectY - 3); moveTo(bx + 24, effectY - 8); lineTo(bx + 28, effectY - 3) }
                                    drawPath(path, style.accent, style = Stroke(1.3f)); label("${effect.semitones}", bx + 27, effectY - 17, 9f)
                                }
                                Effect.Slide -> {
                                    val slideY = if (style.showTab) ty else ny
                                    drawLine(style.accent, Offset(bx + 10, slideY + 5), Offset(bx + 27, slideY - 5), 1.3f)
                                }
                                Effect.HammerOn, Effect.PullOff -> {
                                    val arc = Path().apply { moveTo(bx, effectY + 8); quadraticBezierTo(bx + 15, effectY - 8, bx + 31, effectY + 8) }
                                    drawPath(arc, style.accent, style = Stroke(1.2f)); label(if (effect == Effect.HammerOn) "H" else "P", bx + 12, effectY - 9, 9f)
                                }
                                Effect.Vibrato -> label("~", bx - 3, effectY - 3, 18f)
                                Effect.PalmMute -> label("P.M.", bx - 8, effectY - 3, 9f)
                            }
                            onDrawEffect?.invoke(this, effect, Offset(bx, effectY), style)
                        }
                    }
                    if (style.showStaff && beat.notes.isNotEmpty() && beat.duration != Duration.Whole) {
                        val lowestY = beat.notes.maxOf { y + 4 * gap - it.staffStep * gap / 2 }
                        drawLine(style.ink, Offset(bx + 4, lowestY), Offset(bx + 4, placed.beamY), 1.2f)
                    }
                }
                if (style.showStaff) placed.beams.forEach { beam ->
                    drawLine(style.ink, Offset(beam.from, placed.beamY + beam.level * 5),
                        Offset(beam.to, placed.beamY + beam.level * 5), 3f)
                }
            }
        }
    }
}
