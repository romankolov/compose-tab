package dev.composetab.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import dev.composetab.model.ScorePlaybackPosition

/** Geometry passed to your optional renderer, in logical score coordinates. */
data class ScoreHighlight(
    val kind: HighlightKind, val bounds: Rect, val style: ScoreHighlightStyle,
    val noteId: String? = null, val beatId: String? = null, val tablature: Boolean = false,
)

internal fun measureHighlights(
    placed: PlacedMeasure, selected: Set<String>, playback: ScorePlaybackPosition?, style: ScoreStyle,
): List<ScoreHighlight> = buildList {
    val bottom = placed.y + when {
        style.showTab -> placed.tabOffset + (placed.track.tuning.size - 1) * style.tabStringSpacing +
            (if (!style.showStaff && style.showTabRhythm) 60f else 14f)
        else -> 4 * style.lineSpacing + 14f
    }
    fun addNotes(ids: Set<String>, kind: HighlightKind, appearance: ScoreHighlightStyle) {
        if (appearance.shape == HighlightShape.None) return
        if (appearance.shape == HighlightShape.Capsule) {
            placed.beats.filter { beat -> beat.beat.notes.any { it.id in ids } }.forEach { beat ->
                add(ScoreHighlight(kind, capsuleBounds(placed, beat, style, appearance), appearance, beatId = beat.beat.id))
            }
        } else if (appearance.shape == HighlightShape.VerticalLine || appearance.shape == HighlightShape.BeatColumn) {
            placed.beats.filter { beat -> beat.beat.notes.any { it.id in ids } }.forEach { beat ->
                val next = placed.beats.firstOrNull { it.onset > beat.onset }?.x ?: placed.x + placed.width - 12f
                val left = if (appearance.shape == HighlightShape.VerticalLine) beat.x - appearance.lineWidth / 2 else beat.x - 14f
                val right = if (appearance.shape == HighlightShape.VerticalLine) beat.x + appearance.lineWidth / 2 else next - 14f
                add(ScoreHighlight(kind, Rect(left, placed.y - 16f, right, bottom), appearance, beatId = beat.beat.id))
            }
        } else placed.targets.filter { it.event.noteId in ids && (!it.rhythm || style.tabRhythmStyle == TabRhythmStyle.Notes) }.forEach { target ->
            add(ScoreHighlight(kind, target.bounds.inflate(appearance.padding), appearance, target.event.noteId, target.event.beatId, target.event.tablature))
        }
    }
    addNotes(selected, HighlightKind.Selection, style.selectedHighlight)
    addNotes(playback?.noteIds.orEmpty(), HighlightKind.Playing, style.playingHighlight)
    val cursorXs = mutableListOf<Float>()
    if (style.playhead.shape != HighlightShape.None) playback?.positions.orEmpty().forEach { position ->
        placed.beats.firstOrNull { it.beat.id == position.beatId }?.let { beat ->
            val nextX = placed.beats.firstOrNull { it.onset > beat.onset }?.x ?: placed.x + placed.width - 12f
            val quarter = beat.onset + beat.beat.durationQuarters * position.fraction
            val leftBeat = placed.beats.lastOrNull { it.onset <= quarter } ?: beat
            val rightBeat = placed.beats.firstOrNull { it.onset > quarter }
            val endTime = rightBeat?.onset ?: maxOf(leftBeat.onset + leftBeat.beat.durationQuarters, placed.measure.lengthQuarters?.toFloat() ?: 0f)
            val endX = rightBeat?.x ?: placed.x + placed.width - 12f
            val fraction = ((quarter - leftBeat.onset) / (endTime - leftBeat.onset).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
            val x = leftBeat.x + (endX - leftBeat.x) * fraction
            val key = if (style.playhead.shape == HighlightShape.Capsule) beat.x else x
            if (cursorXs.any { kotlin.math.abs(it - key) < .5f }) return@let
            cursorXs += key
            // A capsule encloses the active beat, rather than sliding off its sounding frets.
            val bounds = if (style.playhead.shape == HighlightShape.Capsule)
                capsuleBounds(placed, beat, style, style.playhead)
            else if (style.playhead.shape == HighlightShape.BeatColumn)
                Rect(beat.x - 14f, placed.y - 16f, nextX - 14f, bottom)
            else Rect(x - style.playhead.lineWidth / 2, placed.y - 16f, x + style.playhead.lineWidth / 2, bottom)
            add(ScoreHighlight(HighlightKind.Playhead, bounds, style.playhead, beatId = beat.beat.id))
        }
    }
    // Hosts may drive only note IDs, without an interpolated beat position.
    if (style.playhead.shape == HighlightShape.Capsule) placed.beats.filter { beat ->
        beat.beat.notes.any { it.id in playback?.noteIds.orEmpty() }
    }.forEach { beat ->
        if (cursorXs.none { kotlin.math.abs(it - beat.x) < .5f }) {
            cursorXs += beat.x
            add(ScoreHighlight(HighlightKind.Playhead, capsuleBounds(placed, beat, style, style.playhead),
                style.playhead, beatId = beat.beat.id))
        }
    }
}

fun DrawScope.drawScoreHighlight(highlight: ScoreHighlight) {
    val b = highlight.bounds
    when (highlight.style.shape) {
        HighlightShape.None -> Unit
        HighlightShape.Capsule -> {
            val radius = minOf(b.width, b.height) / 2
            drawRoundRect(highlight.style.color, b.topLeft, b.size,
                CornerRadius(radius, radius), style = Stroke(highlight.style.lineWidth))
        }
        HighlightShape.VerticalLine -> drawLine(highlight.style.color, Offset(b.center.x, b.top), Offset(b.center.x, b.bottom), highlight.style.lineWidth)
        HighlightShape.NoteHead -> if (highlight.tablature) drawRoundRect(highlight.style.color, b.topLeft, b.size, CornerRadius(4f))
            else drawOval(highlight.style.color, b.topLeft, b.size)
        HighlightShape.Rectangle, HighlightShape.BeatColumn -> drawRoundRect(highlight.style.color, b.topLeft, b.size, CornerRadius(3f))
    }
}

/** Tall outline around the visible staves, with enough width for the whole active fret label. */
internal fun capsuleBounds(placed: PlacedMeasure, beat: PlacedBeat, style: ScoreStyle, appearance: ScoreHighlightStyle): Rect {
    val siblings = placed.beats.filter { it.onset == beat.onset }
    val ids = siblings.map { it.beat.id }.toSet()
    val noteBounds = placed.targets.filter { !it.rhythm && it.event.beatId in ids }.map { it.bounds }
    val padding = appearance.padding + appearance.lineWidth / 2
    val halfWidth = maxOf(14f, if (style.showTab) siblings.flatMap { it.beat.notes }.maxOfOrNull { tabFretLabel(it).length * 4f + 4f } ?: 0f else 14f) + padding
    val top = minOf(if (style.showStaff) placed.y - 10f else placed.y + placed.tabOffset - 10f,
        noteBounds.minOfOrNull { it.top } ?: placed.y) - padding
    val bottom = maxOf(if (style.showTab) placed.y + placed.tabOffset + (placed.track.tuning.size - 1) * style.tabStringSpacing + 10f
        else placed.y + 4 * style.lineSpacing + 10f, noteBounds.maxOfOrNull { it.bottom } ?: placed.y) + padding
    return Rect(beat.x - halfWidth, top, beat.x + halfWidth, bottom)
}
