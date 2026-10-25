package dev.composetab.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.composetab.model.*
import kotlin.math.floor
import kotlin.math.max

internal data class NoteTarget(val bounds: Rect, val event: ScoreEvent.NoteSelected)
internal data class PlacedBeat(val beat: Beat, val x: Float, val onset: Float)
internal data class BeamSegment(val from: Float, val to: Float, val level: Int)
internal data class PlacedMeasure(
    val track: Track, val measure: Measure, val x: Float, val y: Float,
    val beats: List<PlacedBeat>, val width: Float, val beamY: Float,
    val beams: List<BeamSegment>, val targets: List<NoteTarget>,
)
internal data class ScoreLayout(
    val rows: List<List<PlacedMeasure>>, val width: Float, val height: Float, val rowHeight: Float,
) {
    val measures: List<PlacedMeasure> get() = rows.flatten()
    val targets: List<NoteTarget> get() = measures.flatMap { it.targets }

    /** Binary search each vertically visible row; no whole-score scan on each frame. */
    fun visibleMeasures(left: Float, top: Float, right: Float, bottom: Float): List<PlacedMeasure> = buildList {
        rows.forEach { row ->
            if (row.isEmpty() || row.first().y + rowHeight < top || row.first().y - 52 > bottom) return@forEach
            var low = 0
            var high = row.size
            while (low < high) {
                val mid = (low + high) / 2
                if (row[mid].x + row[mid].width < left) low = mid + 1 else high = mid
            }
            var index = low
            while (index < row.size && row[index].x <= right) add(row[index++])
        }
    }

    fun hitTest(point: Offset): NoteTarget? = visibleMeasures(point.x - 14, point.y - 12, point.x + 14, point.y + 12)
        .asReversed().firstNotNullOfOrNull { measure -> measure.targets.lastOrNull { it.bounds.contains(point) } }
}

/** Conventional simple-meter beats or compound-meter dotted beats. Explicit custom beam
 * grouping (e.g. asymmetric 7/8) can replace this policy without changing rendering. */
internal fun beamSegments(beats: List<PlacedBeat>, time: TimeSignature): List<BeamSegment> {
    val groupLength = if (time.denominator == 8 && time.numerator > 3 && time.numerator % 3 == 0) 1.5f
        else 4f / time.denominator
    fun levels(b: PlacedBeat): Int = if (b.beat.isRest) 0 else when (b.beat.duration) {
        Duration.Eighth -> 1; Duration.Sixteenth -> 2; Duration.ThirtySecond -> 3; else -> 0
    }
    fun connected(a: PlacedBeat, b: PlacedBeat, level: Int): Boolean =
        levels(a) > level && levels(b) > level && floor((a.onset + .0001f) / groupLength) == floor((b.onset + .0001f) / groupLength)
    return buildList {
        beats.forEachIndexed { index, beat ->
            repeat(levels(beat)) { level ->
                val next = beats.getOrNull(index + 1)
                val previous = beats.getOrNull(index - 1)
                when {
                    next != null && connected(beat, next, level) -> add(BeamSegment(beat.x + 4, next.x + 4, level))
                    previous != null && connected(previous, beat, level) -> Unit // Already connected from the left.
                    else -> { // Short flag/hook; do not create an extra hook at the end of a beam.
                        val leftHook = previous != null && connected(previous, beat, 0)
                        add(BeamSegment(beat.x + 4, beat.x + if (leftHook) -5 else 13, level))
                    }
                }
            }
        }
    }
}

/** Pure geometry in logical dp. Simultaneous tracks share measure column widths. */
internal fun layoutScore(data: ScoreData, style: ScoreStyle): ScoreLayout {
    val count = data.tracks.maxOfOrNull { it.measures.size } ?: 0
    val widths = List(count) { index ->
        max(style.measureWidth, 80f + (data.tracks.maxOfOrNull { it.measures.getOrNull(index)?.beats?.size ?: 0 } ?: 0) * 40f)
    }
    val offsets = widths.runningFold(32f) { sum, width -> sum + width }
    val rowHeight = max(style.trackSpacing, (if (style.showStaff) 110f else 0f) +
        (data.tracks.maxOfOrNull { it.tuning.size } ?: 6) * style.lineSpacing + 80f)
    val rows = data.tracks.mapIndexed { trackIndex, track ->
        track.measures.mapIndexed { index, measure ->
            val targets = mutableListOf<NoteTarget>()
            val x = offsets[index]
            val width = widths[index]
            val y = 70f + trackIndex * rowHeight
            val total = measure.beats.sumOf { it.durationQuarters.toDouble() }.toFloat().coerceAtLeast(1f)
            var elapsed = 0f
            val extraSpace = (width - 80f - measure.beats.size * 32f).coerceAtLeast(0f)
            val beats = measure.beats.mapIndexed { beatIndex, beat ->
                val bx = x + 54f + beatIndex * 32f + elapsed / total * extraSpace
                val onset = elapsed
                elapsed += beat.durationQuarters
                beat.notes.forEach { note ->
                    require(note.string <= track.tuning.size) { "Note ${note.id} uses a string outside track tuning" }
                    fun target(ny: Float, tab: Boolean) {
                        targets += NoteTarget(Rect(Offset(bx - 13, ny - 11), Offset(bx + 13, ny + 11)),
                            ScoreEvent.NoteSelected(track.id, measure.id, beat.id, note.id, tab))
                    }
                    if (style.showStaff) target(y + 4 * style.lineSpacing - note.staffStep * style.lineSpacing / 2, false)
                    if (style.showTab) target(y + (if (style.showStaff) 110f else 0f) + (note.string - 1) * style.lineSpacing, true)
                }
                PlacedBeat(beat, bx, onset)
            }
            val highest = measure.beats.flatMap { it.notes }.maxOfOrNull { it.staffStep } ?: 0
            val beamY = minOf(y - 12, y + 4 * style.lineSpacing - highest * style.lineSpacing / 2 - 28)
            PlacedMeasure(track, measure, x, y, beats, width, beamY, beamSegments(beats, measure.time), targets)
        }
    }
    return ScoreLayout(rows, offsets.last() + 32f, 40f + data.tracks.size * rowHeight, rowHeight)
}
