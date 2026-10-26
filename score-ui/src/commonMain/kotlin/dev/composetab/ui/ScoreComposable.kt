package dev.composetab.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import dev.composetab.model.*
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import dev.composetab.ui.resources.Res
import dev.composetab.ui.resources.bravura

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
    onDrawHighlight: (DrawScope.(ScoreHighlight) -> Unit)? = null,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val logicalWidth = if (viewport.width == 0) Float.POSITIVE_INFINITY else viewport.width / (density * state.zoom)
    val layout = remember(state.data, style, logicalWidth) { layoutScore(state.data, style, logicalWidth) }
    val zoom by animateFloatAsState(state.zoom, spring(stiffness = 500f), label = "score zoom")
    val text = rememberTextMeasurer(cacheSize = 256)
    val musicFamily = FontFamily(Font(Res.font.bravura))
    val currentEvent by rememberUpdatedState(onEvent)
    val focus = remember { FocusRequester() }
    val beatLocations = remember(layout) { layout.measures.flatMap { m -> m.beats.map { it.beat.id to Offset(it.x, m.y) } }.toMap() }
    val noteLocations = remember(layout) { layout.targets.associate { it.event.noteId to it.bounds.center } }
    LaunchedEffect(layout, viewport, state.zoom, state.revealRequest, state.playback?.positions?.map { it.beatId }, state.playback?.noteIds, state.followPlayback) {
        if (viewport.width == 0 || viewport.height == 0) return@LaunchedEffect
        val width = viewport.width / (density * state.zoom)
        val height = viewport.height / (density * state.zoom)
        val request = state.revealRequest
        val activeBeat = state.playback?.positions?.firstOrNull { it.beatId in beatLocations }?.beatId
        val target = if (request != null) noteLocations[request] ?: beatLocations[request]
            else if (state.followPlayback) activeBeat?.let { beatLocations[it] }
                ?: state.playback?.noteIds?.firstNotNullOfOrNull { noteLocations[it] } else null
        val startX = state.scroll
        val startY = state.verticalScroll
        val endX = (if (target != null && (request != null || target.x < startX + 24 || target.x > startX + width - 48))
            target.x - width * .2f else startX).coerceIn(0f, (layout.width - width).coerceAtLeast(0f))
        val endY = (if (target != null && (request != null || target.y < startY + 24 || target.y + layout.rowHeight > startY + height))
            target.y - 56 else startY).coerceIn(0f, (layout.height - height).coerceAtLeast(0f))
        // Animate the actual viewport state, not just its painted transform: hit tests,
        // culling and host viewport reads stay aligned throughout the transition.
        // A new beat, explicit reveal, resize or manual gesture cancels this animation.
        if (endX != startX || endY != startY) animate(0f, 1f, animationSpec = tween(280)) { fraction, _ ->
            state.scrollTo(startX + (endX - startX) * fraction)
            state.scrollVerticallyTo(startY + (endY - startY) * fraction)
        }
        if (request != null) state.revealRequest = null
    }
    fun viewportEvent() = currentEvent(ScoreEvent.ViewportChanged(state.zoom, state.scroll, state.verticalScroll))
    fun navigate(forward: Boolean): Boolean {
        val targets = layout.targets.filter { it.event.tablature == style.showTab }.distinctBy { it.event.noteId }
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
        .clipToBounds()
        .onSizeChanged { viewport = it }
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
                        state.followPlayback = false
                        val delta = event.changes.fold(Offset.Zero) { acc, change -> acc + change.scrollDelta }
                        val maxX = (layout.width - size.width / (density * state.zoom)).coerceAtLeast(0f)
                        val maxY = (layout.height - size.height / (density * state.zoom)).coerceAtLeast(0f)
                        if (event.keyboardModifiers.isPointerCtrlPressed) state.zoomTo(state.zoom - delta.y * .08f)
                        else if (style.layoutMode == ScoreLayoutMode.Horizontal && layout.rows.size <= 1 && maxX > 0f) state.scrollTo((state.scroll + (delta.x + delta.y) * 32 / state.zoom).coerceIn(0f, maxX))
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
                state.followPlayback = false
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
            val result = text.measure(value, TextStyle(color = if (muted) style.muted else style.ink, fontSize = (font / (density * fontScale)).sp))
            drawText(result, topLeft = Offset(x, y))
        }
        fun DrawScope.music(glyph: String, x: Float, baseline: Float, font: Float = 40f) {
            val result = text.measure(glyph, TextStyle(color = style.ink, fontFamily = musicFamily, fontSize = (font / (density * fontScale)).sp))
            drawText(result, topLeft = Offset(x, baseline - result.firstBaseline))
        }
        withTransform({ scale(factor, factor, Offset.Zero); translate(-scrollX, -scrollY) }) {
            // Cull complete measures before drawing any notation or measuring text.
            layout.visibleMeasures(scrollX, scrollY, scrollX + size.width / factor, scrollY + size.height / factor).forEach { placed ->
                val (track, measure, x, y, beats) = placed
                val gap = style.lineSpacing
                val tabY = y + placed.tabOffset
                val tabGap = style.tabStringSpacing
                val tabRhythm = style.showTab && !style.showStaff && style.showTabRhythm
                val rhythmY = tabY + (track.tuning.size - 1) * tabGap + 50f
                label("${measure.number}", x + 8, y - 27, 11f, true)
                if (placed.startsSystem) label(track.name, x, y - 48, 12f, true)
                fun lines(top: Float, count: Int, spacing: Float = gap) {
                    repeat(count) { i -> drawLine(style.muted.copy(alpha = .55f), Offset(x, top + i * spacing), Offset(x + placed.width, top + i * spacing), .7f) }
                    drawLine(style.ink, Offset(x + placed.width, top), Offset(x + placed.width, top + (count - 1) * spacing), 1f)
                }
                if (style.showStaff) {
                    lines(y, 5)
                    if (placed.startsSystem) music(when (track.clef) { Clef.Treble -> "\uE050"; Clef.Treble8vb -> "\uE052"; Clef.Bass -> "\uE062" },
                        x - 24, y + if (track.clef == Clef.Bass) gap else 3 * gap, gap * 4)
                    label("${measure.time.numerator}", x + 14, y + 1, 17f)
                    label("${measure.time.denominator}", x + 14, y + 20, 17f)
                }
                if (style.showTab) { lines(tabY, track.tuning.size, tabGap); label("TAB", x + 4, tabY + 17, 10f, true) }
                fun drawRepeat(rx: Float, start: Boolean, span: RepeatSpan) {
                    drawLine(style.ink, Offset(rx, span.top), Offset(rx, span.bottom), 2.5f)
                    listOf(span.upperDot, span.lowerDot).forEach { dotY ->
                        drawCircle(style.ink, 1.8f, Offset(rx + if (start) 6 else -6, dotY))
                    }
                }
                listOf(true, false).forEach { start ->
                    if (if (start) measure.repeatStart else measure.repeatEnd) {
                        val rx = if (start) x + 3 else x + placed.width - 5
                        if (style.showStaff) drawRepeat(rx, start, repeatSpan(y, 5, gap))
                        if (style.showTab) drawRepeat(rx, start, repeatSpan(tabY, track.tuning.size, tabGap))
                    }
                }
                // Erase tab line segments first; highlights then remain visible behind fret text.
                if (style.showTab) beats.forEach { placedBeat -> placedBeat.beat.notes.forEach { note ->
                    val ty = tabY + (note.string - 1) * tabGap
                    val font = if (placedBeat.beat.grace != null) 9f else 13f
                    val width = text.measure(tabFretLabel(note), TextStyle(fontSize = (font / (density * fontScale)).sp)).size.width.toFloat()
                    drawRect(style.paper, Offset(placedBeat.x - width / 2 - 2, ty - 7), Size(width + 4, 14f))
                } }
                measureHighlights(placed, state.selectedNoteIds, state.playback, style).forEach { highlight ->
                    if (onDrawHighlight != null) onDrawHighlight.invoke(this, highlight) else drawScoreHighlight(highlight)
                }
                beats.forEachIndexed { index, (beat, bx) ->
                    if (beat.grace != null) {
                        // Ornaments have their own small glyphs/stems, never join metrical beams.
                        fun smallStem(baseY: Float) {
                            if (beat.duration == Duration.Whole) return
                            drawLine(style.ink, Offset(bx + 3, baseY), Offset(bx + 3, baseY - 20), .9f)
                            val flags = when (beat.duration) {
                                Duration.Eighth -> 1; Duration.Sixteenth -> 2; Duration.ThirtySecond -> 3; else -> 0
                            }
                            repeat(flags) { flag ->
                                val top = baseY - 20 + flag * 4
                                drawPath(Path().apply { moveTo(bx + 3, top); quadraticTo(bx + 12, top + 5, bx + 6, top + 10) }, style.ink, style = Stroke(.9f))
                            }
                            if (beat.grace == GraceKind.BeforeBeat) drawLine(style.ink, Offset(bx - 1, baseY - 11), Offset(bx + 9, baseY - 18), 1f)
                        }
                        if (beat.isRest) {
                            val glyph = when (beat.duration) {
                                Duration.Whole -> "\uE4E3"; Duration.Half -> "\uE4E4"; Duration.Quarter -> "\uE4E5"
                                Duration.Eighth -> "\uE4E6"; Duration.Sixteenth -> "\uE4E7"; Duration.ThirtySecond -> "\uE4E8"
                            }
                            if (style.showStaff) music(glyph, bx - 3, y + 2 * gap, 25f)
                            if (style.showTab) music(glyph, bx - 3, tabY + 2 * tabGap, 25f)
                        }
                        beat.notes.forEach { note ->
                            if (style.showStaff) {
                                val gy = y + 4 * gap - note.staffStep * gap / 2
                                if (note.staffStep < 0) for (step in -2 downTo note.staffStep step 2) drawLine(style.ink, Offset(bx - 5, y + 4 * gap - step * gap / 2), Offset(bx + 5, y + 4 * gap - step * gap / 2), .7f)
                                if (note.staffStep > 8) for (step in 10..note.staffStep step 2) drawLine(style.ink, Offset(bx - 5, y + 4 * gap - step * gap / 2), Offset(bx + 5, y + 4 * gap - step * gap / 2), .7f)
                                drawOval(style.ink, Offset(bx - 3, gy - 2), Size(6f, 4f),
                                    style = if (beat.duration in listOf(Duration.Whole, Duration.Half)) Stroke(.9f) else Fill)
                                note.accidental?.let { music(when (it) { Accidental.Sharp -> "\uE262"; Accidental.Flat -> "\uE260"; Accidental.Natural -> "\uE261" }, bx - 11, gy, 25f) }
                                smallStem(gy)
                            }
                            if (style.showTab) {
                                val gy = tabY + (note.string - 1) * tabGap
                                val fret = tabFretLabel(note)
                                val width = text.measure(fret, TextStyle(fontSize = (9f / (density * fontScale)).sp)).size.width.toFloat()
                                label(fret, bx - width / 2, gy - 6, 9f)
                                smallStem(gy - 4)
                            }
                            val earlier = beats.take(index).any { it.onset == beats[index].onset && it.beat.grace != null &&
                                it.beat.voice == beat.voice && it.beat.notes.any { n -> n.string == note.string } }
                            val anchor = beats.drop(index + 1).firstOrNull { it.beat.grace == null && it.beat.voice == beat.voice && it.onset == beats[index].onset }
                            val target = anchor?.beat?.notes?.firstOrNull { it.string == note.string }
                            if (!earlier && anchor != null && target != null) {
                                fun graceSlur(fromY: Float, toY: Float) {
                                    drawPath(Path().apply { moveTo(bx + 4, fromY); quadraticTo((bx + anchor.x) / 2,
                                        maxOf(fromY, toY) + 9, anchor.x - 6, toY) }, style.ink, style = Stroke(.9f))
                                }
                                if (style.showStaff) graceSlur(y + 4 * gap - note.staffStep * gap / 2 + 5,
                                    y + 4 * gap - target.staffStep * gap / 2 + 6)
                                if (style.showTab) graceSlur(tabY + (note.string - 1) * tabGap + 6,
                                    tabY + (target.string - 1) * tabGap + 7)
                            }
                            note.effects.forEach { effect -> onDrawEffect?.invoke(this, effect,
                                Offset(bx, (if (style.showTab) tabY + (note.string - 1) * tabGap else y + 4 * gap - note.staffStep * gap / 2) - 8), style) }
                        }
                        return@forEachIndexed
                    }
                    if (beat.isRest) {
                        if (style.showStaff || tabRhythm) {
                            val rest = when (beat.duration) {
                                Duration.Whole -> "\uE4E3"; Duration.Half -> "\uE4E4"
                                Duration.Quarter -> "\uE4E5"; Duration.Eighth -> "\uE4E6"
                                Duration.Sixteenth -> "\uE4E7"; Duration.ThirtySecond -> "\uE4E8"
                            }
                            music(rest, bx - 5, if (tabRhythm) rhythmY else y + 2 * gap, gap * 4)
                            if (beat.dotted) drawCircle(style.ink, 1.6f, Offset(bx + 10, if (tabRhythm) rhythmY - 2 else y + 1.5f * gap))
                        }
                        if (style.showTab) label("–", bx - 4, tabY + 16, 13f, true)
                    }
                    // Harmonic captions belong above the whole tab, never on another string.
                    val caption = harmonicCaption(beat.notes)
                    if (caption.isNotEmpty()) {
                        val captionWidth = text.measure(caption, TextStyle(fontSize = (10f / (density * fontScale)).sp)).size.width.toFloat()
                        label(caption, bx - captionWidth / 2, (if (style.showTab) tabY else y) - 26f, 10f, true)
                    }
                    beat.notes.forEach { note ->
                        val ny = y + 4 * gap - note.staffStep * gap / 2
                        val ty = tabY + (note.string - 1) * tabGap
                        if (style.showStaff) {
                            // Ledger lines on every second staff step outside the five-line staff.
                            if (note.staffStep < 0) for (step in -2 downTo note.staffStep step 2) drawLine(style.ink, Offset(bx - 9, y + 4 * gap - step * gap / 2), Offset(bx + 9, y + 4 * gap - step * gap / 2), .8f)
                            if (note.staffStep > 8) for (step in 10..note.staffStep step 2) drawLine(style.ink, Offset(bx - 9, y + 4 * gap - step * gap / 2), Offset(bx + 9, y + 4 * gap - step * gap / 2), .8f)
                            if (Effect.DeadNote in note.effects || Effect.Slap in note.effects) {
                                drawLine(style.ink, Offset(bx - 4, ny - 4), Offset(bx + 4, ny + 4), 1.5f)
                                drawLine(style.ink, Offset(bx - 4, ny + 4), Offset(bx + 4, ny - 4), 1.5f)
                            } else drawOval(style.ink, Offset(bx - 5, ny - 3.5f), Size(10f, 7f), style = if (beat.duration in listOf(Duration.Whole, Duration.Half)) Stroke(1.5f) else Fill)
                            note.accidental?.let { music(when (it) { Accidental.Sharp -> "\uE262"; Accidental.Flat -> "\uE260"; Accidental.Natural -> "\uE261" }, bx - 17, ny, gap * 4) }
                            if (beat.dotted) drawCircle(style.ink, 1.6f, Offset(bx + 10, ny - 2))
                        }
                        if (style.showTab) {
                            val fret = tabFretLabel(note)
                            val labelWidth = text.measure(fret, TextStyle(fontSize = (13f / (density * fontScale)).sp)).size.width.toFloat()
                            label(fret, bx - labelWidth / 2, ty - 8, 13f)
                        }
                        val effectY = if (style.showTab) ty - 12 else ny - 12
                        val nextOnString = beats.drop(index + 1).firstOrNull { next -> next.onset > beats[index].onset && next.beat.notes.any { it.string == note.string } }
                        val effectEnd = nextOnString?.x ?: bx + 31f
                        note.effects.forEach { effect ->
                            when (effect) {
                                is Effect.Bend -> {
                                    val base = if (style.showTab) ty else ny
                                    val height = 28f + effect.semitones.coerceAtMost(2f) * 8f
                                    val tipX = bx + 7 + (effectEnd - bx - 14).coerceIn(18f, 42f)
                                    val tipY = base - height
                                    val curve = Path().apply {
                                        moveTo(bx + 7, base - 2)
                                        cubicTo(tipX - 5, base - 2, tipX, base - height * .65f, tipX, tipY)
                                    }
                                    drawPath(curve, style.ink, style = Stroke(1.3f))
                                    drawPath(Path().apply { moveTo(tipX, tipY); lineTo(tipX - 4, tipY + 7); lineTo(tipX + 4, tipY + 7); close() }, style.ink)
                                    label(when (effect.semitones) { .5f -> "¼"; 1f -> "½"; 2f -> "full"; else -> "${effect.semitones} st" }, tipX - 8, tipY - 15, 10f)
                                    if (effect.hold) drawLine(style.ink, Offset(tipX + 3, tipY), Offset(maxOf(tipX + 16, effectEnd - 7), tipY), 1f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                                    if (effect.release) drawPath(Path().apply {
                                        moveTo(tipX, tipY + 8); quadraticTo(tipX + 8, base - 4, maxOf(tipX + 16, effectEnd - 7), base - 2)
                                    }, style.ink, style = Stroke(1.2f))
                                }
                                Effect.Slide -> {
                                    val slideY = if (style.showTab) ty else ny
                                    drawLine(style.ink, Offset(bx + 10, slideY + 5), Offset(effectEnd - 9, slideY - 5), 1.3f)
                                }
                                Effect.HammerOn, Effect.PullOff -> {
                                    // One continuous slur for a connected run, rather than
                                    // repeating H/P labels and separate bumps on every fret.
                                    val previous = beats.take(index).lastOrNull { it.onset < beats[index].onset && it.beat.notes.any { n -> n.string == note.string } }
                                        ?.beat?.notes?.firstOrNull { it.string == note.string }
                                    if (previous?.effects?.any { it == Effect.HammerOn || it == Effect.PullOff } != true) {
                                        var end = effectEnd
                                        var endNote = note
                                        for (next in beats.drop(index + 1).filter { it.onset > beats[index].onset }) {
                                            val n = next.beat.notes.firstOrNull { it.string == note.string } ?: break
                                            end = next.x
                                            endNote = n
                                            if (n.effects.none { it == Effect.HammerOn || it == Effect.PullOff }) break
                                        }
                                        fun fretWidth(n: Note) = text.measure(tabFretLabel(n), TextStyle(fontSize = (13f / (density * fontScale)).sp)).size.width.toFloat()
                                        val (left, right) = legatoEdges(bx, if (style.showTab) fretWidth(note) else 10f,
                                            end, if (style.showTab) fretWidth(endNote) else 10f)
                                        val arc = Path().apply { moveTo(left, effectY + 3); quadraticTo((left + right) / 2, effectY - 15, right, effectY + 3) }
                                        drawPath(arc, style.ink, style = Stroke(1.3f))
                                        if (style.showLegatoLabels) label(if (effect == Effect.HammerOn) "H" else "P", (bx + end) / 2 - 3, effectY - 11, 9f)
                                    }
                                }
                                Effect.Vibrato -> {
                                    val bend = note.effects.filterIsInstance<Effect.Bend>().firstOrNull()
                                    val waveY = (if (style.showTab) ty else ny) - if (bend != null) 50f + bend.semitones.coerceAtMost(2f) * 8f else 22f
                                    val waveX = if (bend != null) bx + 3 + (effectEnd - bx - 14).coerceIn(18f, 42f) else bx - 4
                                    val wave = Path().apply {
                                        moveTo(waveX, waveY)
                                        repeat(12) { i -> lineTo(waveX + (i + 1) * 3f, waveY + if (i % 2 == 0) -2.5f else 2.5f) }
                                    }
                                    drawPath(wave, style.ink, style = Stroke(2f))
                                }
                                Effect.DeadNote, Effect.Slap -> Unit // Fret is replaced by x, not a sounding note head.
                                Effect.PalmMute -> label("P.M.", bx + 8, effectY - 3, 8f)
                                Effect.Ghost -> Unit // Parentheses are part of the fret label.
                                is Effect.Harmonic -> {
                                    if (!style.showTab) {
                                        val diamond = Path().apply { moveTo(bx, ny - 6); lineTo(bx + 6, ny); lineTo(bx, ny + 6); lineTo(bx - 6, ny); close() }
                                        drawPath(diamond, style.ink, style = Stroke(1f))
                                    }

                                }
                            }
                            onDrawEffect?.invoke(this, effect, Offset(bx, effectY), style)
                        }
                    }
                    if (style.showTab && beat.notes.isNotEmpty()) beat.strum?.let { direction ->
                        val top = tabY + (beat.notes.minOf { it.string } - 1) * tabGap - 5
                        val bottom = tabY + (beat.notes.maxOf { it.string } - 1) * tabGap + 5
                        val tip = if (direction == StrumDirection.Down) bottom else top
                        val tail = if (direction == StrumDirection.Down) top else bottom
                        val head = if (direction == StrumDirection.Down) -5f else 5f
                        drawLine(style.ink, Offset(bx - 24, tail), Offset(bx - 24, tip), 1.4f)
                        drawLine(style.ink, Offset(bx - 28, tip + head), Offset(bx - 24, tip), 1.4f)
                        drawLine(style.ink, Offset(bx - 20, tip + head), Offset(bx - 24, tip), 1.4f)
                    }
                    if (tabRhythm && beat.notes.isNotEmpty()) {
                        if (style.tabRhythmStyle == TabRhythmStyle.Notes) {
                            drawOval(style.ink, Offset(bx - 4, rhythmY - 3), Size(8f, 6f),
                                style = if (beat.duration in listOf(Duration.Whole, Duration.Half)) Stroke(1.3f) else Fill)
                        } else if (beat.duration == Duration.Whole) {
                            drawLine(style.muted, Offset(bx - 2, rhythmY), Offset(bx + 10, rhythmY), 1.5f)
                        } else if (beat.duration == Duration.Half) {
                            // Small duration tick distinguishes a half from a quarter without a head.
                            drawLine(style.muted, Offset(bx, rhythmY - 6), Offset(bx + 8, rhythmY - 6), 1.5f)
                        }
                        if (beat.duration != Duration.Whole) drawLine(if (style.tabRhythmStyle == TabRhythmStyle.Beams) style.muted else style.ink,
                            Offset(bx + 4, rhythmY), Offset(bx + 4, rhythmY - 28), 1.2f)
                        if (beat.dotted) drawCircle(style.ink, 1.6f, Offset(bx + 10, rhythmY - 8))
                    }
                    if (style.showStaff && beat.notes.isNotEmpty() && beat.duration != Duration.Whole) {
                        val lowestY = beat.notes.maxOf { y + 4 * gap - it.staffStep * gap / 2 }
                        drawLine(style.ink, Offset(bx + 4, lowestY), Offset(bx + 4, placed.beamY), 1.2f)
                    }
                }
                placed.ties.forEach { tie ->
                    clipRect(left = placed.x, right = placed.x + placed.width,
                        top = minOf(tie.from.y, tie.to.y) - 1, bottom = maxOf(tie.from.y, tie.to.y) + 15) {
                        drawPath(Path().apply {
                            moveTo(tie.from.x, tie.from.y)
                            quadraticTo((tie.from.x + tie.to.x) / 2, maxOf(tie.from.y, tie.to.y) + 13f, tie.to.x, tie.to.y)
                        }, style.ink, style = Stroke(1.2f))
                    }
                }
                if (style.showStaff || tabRhythm) placed.beams.forEach { beam ->
                    val bare = tabRhythm && style.tabRhythmStyle == TabRhythmStyle.Beams
                    val beamY = if (tabRhythm) (if (bare) rhythmY else rhythmY - 28) else placed.beamY
                    val levelOffset = beam.level * if (bare) -5f else 5f
                    drawLine(if (bare) style.muted else style.ink, Offset(beam.from, beamY + levelOffset),
                        Offset(beam.to, beamY + levelOffset), 3f)
                }
            }
        }
    }
}
