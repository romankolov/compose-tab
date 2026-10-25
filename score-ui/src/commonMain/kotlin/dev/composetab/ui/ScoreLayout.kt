package dev.composetab.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.composetab.model.*

internal data class NoteTarget(val bounds: Rect, val event: ScoreEvent.NoteSelected)
internal data class PlacedBeat(val beat: Beat, val x: Float)
internal data class PlacedMeasure(val track: Track, val measure: Measure, val x: Float, val y: Float, val beats: List<PlacedBeat>)
internal data class ScoreLayout(val measures: List<PlacedMeasure>, val targets: List<NoteTarget>, val width: Float, val height: Float)

/** Pure geometry; independent of Canvas, density and viewport. Units are logical dp. */
internal fun layoutScore(data: ScoreData, style: ScoreStyle): ScoreLayout {
    val targets = mutableListOf<NoteTarget>()
    val measures = data.tracks.flatMapIndexed { trackIndex, track ->
        track.measures.mapIndexed { index, measure ->
            val x = 32f + index * style.measureWidth
            val y = 56f + trackIndex * style.trackSpacing
            val total = measure.beats.sumOf { (it.duration.quarters * if (it.dotted) 1.5f else 1f).toDouble() }.toFloat().coerceAtLeast(1f)
            var elapsed = 0f
            val beats = measure.beats.map { beat ->
                val bx = x + 54f + elapsed / total * (style.measureWidth - 80f)
                elapsed += beat.duration.quarters * if (beat.dotted) 1.5f else 1f
                beat.notes.forEach { note ->
                    require(note.string <= track.tuning.size) { "Note ${note.id} uses a string outside track tuning" }
                    fun target(ny: Float, tab: Boolean) {
                        targets += NoteTarget(Rect(Offset(bx - 13, ny - 11), Offset(bx + 13, ny + 11)),
                            ScoreEvent.NoteSelected(track.id, measure.id, beat.id, note.id, tab))
                    }
                    if (style.showStaff) target(y + 4 * style.lineSpacing - note.staffStep * style.lineSpacing / 2, false)
                    if (style.showTab) target(y + (if (style.showStaff) 110f else 0f) + (note.string - 1) * style.lineSpacing, true)
                }
                PlacedBeat(beat, bx)
            }
            PlacedMeasure(track, measure, x, y, beats)
        }
    }
    return ScoreLayout(measures, targets, 64f + (data.tracks.maxOfOrNull { it.measures.size } ?: 0) * style.measureWidth,
        80f + data.tracks.size * style.trackSpacing)
}
