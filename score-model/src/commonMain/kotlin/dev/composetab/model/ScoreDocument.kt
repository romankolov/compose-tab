package dev.composetab.model

/** Application-owned decoder boundary. Implement this for GP, MusicXML, a database, etc.
 * No bytes, I/O, audio clocks or decoder dependencies are needed by the UI library. */
fun interface ScoreAdapter<in Source> { fun adapt(source: Source): ScoreDocument }

data class ScoreDocument(val data: ScoreData, val timeline: ScoreTimeline = ScoreTimeline.from(data))

/** A position in a notated beat. IDs are the same stable IDs used by selection events. */
data class ScorePosition(val beatId: String, val fraction: Float = 0f) {
    init { require(beatId.isNotBlank()); require(fraction.isFinite() && fraction in 0f..1f) }
}
data class ScorePlaybackPosition(
    val positions: List<ScorePosition> = emptyList(),
    val noteIds: Set<String> = emptySet(),
)

data class ScoreMoment(
    val trackId: String, val measureId: String, val beatId: String,
    val startQuarter: Double, val durationQuarters: Double, val noteIds: Set<String>,
) {
    init { require(startQuarter.isFinite() && startQuarter >= 0); require(durationQuarters.isFinite() && durationQuarters > 0) }
    val endQuarter: Double get() = startQuarter + durationQuarters
}

/** A timing *index*, not a player. Feed it quarters from your audio engine. Repeat passes
 * can reference the same beat IDs at different startQuarter values. End is exclusive. */
class ScoreTimeline(moments: List<ScoreMoment>) {
    val moments: List<ScoreMoment> = moments.sortedBy { it.startQuarter }
    val durationQuarters: Double = moments.maxOfOrNull { it.endQuarter } ?: 0.0
    private val prefixEnds = DoubleArray(this.moments.size).also { ends ->
        this.moments.forEachIndexed { index, moment -> ends[index] = maxOf(moment.endQuarter, ends.getOrElse(index - 1) { 0.0 }) }
    }
    fun at(quarter: Double): ScorePlaybackPosition {
        require(quarter.isFinite() && quarter >= 0)
        var low = 0; var high = moments.size
        while (low < high) { val mid = (low + high) / 2; if (moments[mid].startQuarter <= quarter) low = mid + 1 else high = mid }
        val active = mutableListOf<ScoreMoment>()
        var index = low - 1
        while (index >= 0 && prefixEnds[index] > quarter) {
            moments[index].takeIf { quarter < it.endQuarter }?.let(active::add)
            index--
        }
        return ScorePlaybackPosition(active.map { ScorePosition(it.beatId, ((quarter - it.startQuarter) / it.durationQuarters).toFloat().coerceIn(0f, 1f)) }, active.flatMap { it.noteIds }.toSet())
    }
    companion object {
        fun from(data: ScoreData): ScoreTimeline = ScoreTimeline(data.tracks.flatMap { track ->
            var measureStart = 0.0
            track.measures.flatMap { measure ->
                var beatStart = 0.0
                val moments = measure.beats.map { beat ->
                    val onset = beat.offsetQuarters ?: beatStart
                    beatStart = maxOf(beatStart, onset + beat.durationQuarters)
                    ScoreMoment(track.id, measure.id, beat.id, measureStart + onset, beat.durationQuarters.toDouble(), beat.notes.map { it.id }.toSet())
                }
                measureStart += measure.lengthQuarters ?: maxOf(measure.time.numerator * 4.0 / measure.time.denominator, beatStart)
                moments
            }
        })
    }
}
