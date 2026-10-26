package dev.composetab.ui

import androidx.compose.ui.geometry.Offset
import dev.composetab.model.*

/** Per-measure fragments keep ties visible when either endpoint is offscreen or on another system. */
internal data class TieSegment(val from: Offset, val to: Offset)
private data class TieAnchor(val measure: PlacedMeasure, val beat: PlacedBeat, val note: Note)

internal fun withTies(rows: List<List<PlacedMeasure>>, style: ScoreStyle): List<List<PlacedMeasure>> {
    val fragments = mutableMapOf<String, MutableList<TieSegment>>()
    rows.flatten().groupBy { it.track.id }.values.forEach { track ->
        val indices = track.mapIndexed { i, m -> m.measure.id to i }.toMap()
        val anchors = track.flatMap { m -> m.beats.flatMap { b -> b.beat.notes.map { n -> n.id to TieAnchor(m, b, n) } } }.toMap()
        anchors.values.forEach { target ->
            val source = target.note.tieFrom?.let { anchors[it] } ?: return@forEach
            val first = indices.getValue(source.measure.measure.id)
            val last = indices.getValue(target.measure.measure.id)
            // One curve per system; identical clipped fragments meet smoothly at barlines.
            (first..last).groupBy { track[it].y }.values.forEach { group ->
                val startMeasure = track[group.first()]
                val endMeasure = track[group.last()]
                fun fragment(tab: Boolean) {
                    fun ny(n: Note): Float = if (tab) startMeasure.y + startMeasure.tabOffset + (n.string - 1) * style.tabStringSpacing + 8f
                        else startMeasure.y + 4 * style.lineSpacing - n.staffStep * style.lineSpacing / 2 + 6f
                    fun pad(n: Note) = if (tab) tabFretLabel(n).length * 3.5f + 2 else 7f
                    val left = if (group.first() == first) source.beat.x + pad(source.note) else startMeasure.x + 24f
                    val right = if (group.last() == last) target.beat.x - pad(target.note) else endMeasure.x + endMeasure.width - 3f
                    if (right > left) {
                        val segment = TieSegment(Offset(left, ny(source.note)), Offset(right, ny(target.note)))
                        group.forEach { i -> fragments.getOrPut(track[i].measure.id) { mutableListOf() } += segment }
                    }
                }
                if (style.showStaff) fragment(false)
                if (style.showTab) fragment(true)
            }
        }
    }
    return rows.map { row -> row.map { it.copy(ties = fragments[it.measure.id].orEmpty()) } }
}
