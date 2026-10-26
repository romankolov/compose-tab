package dev.composetab.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
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
        if (appearance.shape == HighlightShape.VerticalLine || appearance.shape == HighlightShape.BeatColumn) {
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
            if (cursorXs.any { kotlin.math.abs(it - x) < .5f }) return@let
            cursorXs += x
            val bounds = if (style.playhead.shape == HighlightShape.BeatColumn)
                Rect(beat.x - 14f, placed.y - 16f, nextX - 14f, bottom)
            else Rect(x - style.playhead.lineWidth / 2, placed.y - 16f, x + style.playhead.lineWidth / 2, bottom)
            add(ScoreHighlight(HighlightKind.Playhead, bounds, style.playhead, beatId = beat.beat.id))
        }
    }
}

fun DrawScope.drawScoreHighlight(highlight: ScoreHighlight) {
    val b = highlight.bounds
    when (highlight.style.shape) {
        HighlightShape.None -> Unit
        HighlightShape.VerticalLine -> drawLine(highlight.style.color, Offset(b.center.x, b.top), Offset(b.center.x, b.bottom), highlight.style.lineWidth)
        HighlightShape.NoteHead -> if (highlight.tablature) drawRoundRect(highlight.style.color, b.topLeft, b.size, CornerRadius(4f))
            else drawOval(highlight.style.color, b.topLeft, b.size)
        HighlightShape.Rectangle, HighlightShape.BeatColumn -> drawRoundRect(highlight.style.color, b.topLeft, b.size, CornerRadius(3f))
    }
}
