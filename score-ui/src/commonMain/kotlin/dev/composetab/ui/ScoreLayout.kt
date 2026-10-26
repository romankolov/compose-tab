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
    val beams: List<BeamSegment>, val targets: List<NoteTarget>, val startsSystem: Boolean = false, val tabOffset: Float = 110f,
)
internal data class ScoreLayout(
    val rows: List<List<PlacedMeasure>>, val width: Float, val height: Float, val rowHeight: Float,
) {
    val measures: List<PlacedMeasure> get() = rows.flatten()
    val targets: List<NoteTarget> get() = measures.flatMap { it.targets }

    /** Binary search each vertically visible row; no whole-score scan on each frame. */
    fun visibleMeasures(left: Float, top: Float, right: Float, bottom: Float): List<PlacedMeasure> = buildList {
        val firstRow = (floor((top - 70f) / rowHeight).toInt() - 1).coerceIn(0, rows.size)
        val lastRow = (floor((bottom - 18f) / rowHeight).toInt() + 2).coerceIn(firstRow, rows.size)
        rows.subList(firstRow, lastRow).forEach { row ->
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
internal fun layoutScore(data: ScoreData, style: ScoreStyle, viewportWidth: Float = Float.POSITIVE_INFINITY): ScoreLayout {
    val count = data.tracks.maxOfOrNull { it.measures.size } ?: 0
    val widths = List(count) { index ->
        max(style.measureWidth, 80f + (data.tracks.maxOfOrNull { it.measures.getOrNull(index)?.beats?.size ?: 0 } ?: 0) * 40f)
    }
    val wrapping = style.layoutMode == ScoreLayoutMode.Vertical && viewportWidth.isFinite()
    val available = (viewportWidth - 64f).coerceAtLeast(140f)
    val actualWidths = (if (wrapping) widths.map { it.coerceAtMost(available) } else widths).toMutableList()
    var columnX = 32f
    var system = 0
    val slots = actualWidths.map { width ->
        if (wrapping && columnX > 32f && columnX + width > viewportWidth - 32f) { columnX = 32f; system++ }
        (columnX to system).also { columnX += width }
    }.toMutableList()
    val systemCount = if (count == 0) 0 else system + 1
    if (wrapping && style.justifySystems) (0 until systemCount).forEach { systemIndex ->
        if (systemIndex < systemCount - 1 || style.justifyLastSystem) {
            val indices = slots.indices.filter { slots[it].second == systemIndex }
            val extra = ((viewportWidth - 64f - indices.sumOf { actualWidths[it].toDouble() }.toFloat()) / indices.size.coerceAtLeast(1)).coerceAtLeast(0f)
            var x = 32f
            indices.forEach { index -> actualWidths[index] += extra; slots[index] = x to systemIndex; x += actualWidths[index] }
        }
    }
    val tabOffsets = data.tracks.associate { track ->
        val lowest = track.measures.flatMap { it.beats }.flatMap { it.notes }.minOfOrNull { it.staffStep } ?: 0
        track.id to if (style.showStaff) maxOf(110f, 4 * style.lineSpacing - lowest * style.lineSpacing / 2 + 30f) else 0f
    }
    val rowHeight = if (!style.showStaff && style.showTab) {
        // Tab-only height responds to string spacing instead of retaining the
        // notation system's 270-unit minimum (which left large empty bands).
        (data.tracks.maxOfOrNull { (it.tuning.size - 1) * style.tabStringSpacing } ?: 0f) +
            (if (style.showTabRhythm) 60f else 18f) + style.tabSystemSpacing
    } else max(style.trackSpacing, data.tracks.maxOfOrNull { track ->
        if (style.showTab) tabOffsets.getValue(track.id) + track.tuning.size * style.tabStringSpacing + 80f else 90f
    } ?: 270f)
    val rows = (0 until systemCount).flatMap { systemIndex -> data.tracks.mapIndexed { trackIndex, track ->
        track.measures.mapIndexedNotNull { index, measure ->
            if (slots[index].second != systemIndex) return@mapIndexedNotNull null
            val targets = mutableListOf<NoteTarget>()
            val tabOffset = tabOffsets.getValue(track.id)
            val x = slots[index].first
            val width = actualWidths[index]
            val y = 70f + (systemIndex * data.tracks.size + trackIndex) * rowHeight
            var elapsed = 0f
            val onsets = measure.beats.map { beat ->
                val start = beat.offsetQuarters?.toFloat() ?: elapsed
                elapsed = maxOf(elapsed, start + beat.durationQuarters)
                start
            }
            val total = maxOf(elapsed, measure.lengthQuarters?.toFloat() ?: 0f, 1f)
            val distinctOnsets = onsets.distinct().sorted()
            val spacing = minOf(32f, (width - 80f) / distinctOnsets.size.coerceAtLeast(1))
            val extraSpace = (width - 80f - distinctOnsets.size * spacing).coerceAtLeast(0f)
            val beats = measure.beats.mapIndexed { beatIndex, beat ->
                val onset = onsets[beatIndex]
                val bx = x + 54f + distinctOnsets.binarySearch(onset) * spacing + onset / total * extraSpace
                beat.notes.forEach { note ->
                    require(note.string <= track.tuning.size) { "Note ${note.id} uses a string outside track tuning" }
                    fun target(ny: Float, tab: Boolean) {
                        targets += NoteTarget(Rect(Offset(bx - 13, ny - 11), Offset(bx + 13, ny + 11)),
                            ScoreEvent.NoteSelected(track.id, measure.id, beat.id, note.id, tab))
                    }
                    if (style.showStaff) target(y + 4 * style.lineSpacing - note.staffStep * style.lineSpacing / 2, false)
                    if (style.showTab) target(y + tabOffset + (note.string - 1) * style.tabStringSpacing, true)
                    if (style.showTab && !style.showStaff && style.showTabRhythm)
                        target(y + (track.tuning.size - 1) * style.tabStringSpacing + 50f, true)
                }
                PlacedBeat(beat, bx, onset)
            }.sortedBy { it.onset }
            val highest = measure.beats.flatMap { it.notes }.maxOfOrNull { it.staffStep } ?: 0
            val beamY = minOf(y - 12, y + 4 * style.lineSpacing - highest * style.lineSpacing / 2 - 28)
            PlacedMeasure(track, measure, x, y, beats, width, beamY, beamSegments(beats, measure.time), targets, x == 32f, tabOffset)
        }
    } }
    val contentWidth = rows.maxOfOrNull { row -> row.maxOfOrNull { it.x + it.width + 32f } ?: 64f } ?: 64f
    return ScoreLayout(rows, contentWidth, maxOf(80f, 40f + systemCount * data.tracks.size * rowHeight), rowHeight)
}
