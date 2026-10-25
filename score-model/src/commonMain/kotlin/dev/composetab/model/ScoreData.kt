package dev.composetab.model

/** Decoder-independent, UI-only value objects. IDs must be stable and unique within a score.
 * Lists are treated as immutable: publish a new ScoreData when editing. */
data class ScoreData(val title: String, val composer: String = "", val tracks: List<Track>) {
    init {
        val ids = tracks.flatMap { t -> listOf(t.id) + t.measures.flatMap { m ->
            listOf(m.id) + m.beats.flatMap { b -> listOf(b.id) + b.notes.map { it.id } }
        } }
        require(ids.all { it.isNotBlank() }) { "Score IDs must not be blank" }
        require(ids.size == ids.toSet().size) { "Score IDs must be globally unique" }
    }
}
data class Track(
    val id: String, val name: String, val tuning: List<Int> = listOf(64, 59, 55, 50, 45, 40),
    val measures: List<Measure>,
) { init { require(tuning.isNotEmpty()); require(tuning.all { it in 0..127 }) } }
data class TimeSignature(val numerator: Int = 4, val denominator: Int = 4) {
    init { require(numerator > 0); require(denominator in listOf(1, 2, 4, 8, 16, 32)) }
}
data class Measure(
    val id: String, val number: Int, val time: TimeSignature = TimeSignature(),
    val beats: List<Beat>, val repeatStart: Boolean = false, val repeatEnd: Boolean = false,
    val lengthQuarters: Double? = null,
) {
    init { require(lengthQuarters == null || (lengthQuarters.isFinite() && lengthQuarters > 0)) }
}
enum class Duration(val quarters: Float) {
    Whole(4f), Half(2f), Quarter(1f), Eighth(.5f), Sixteenth(.25f), ThirtySecond(.125f)
}
data class Beat(
    val id: String, val duration: Duration = Duration.Quarter, val dotted: Boolean = false,
    val notes: List<Note> = emptyList(),
    /** Explicit decoder timing supports tuplets and simultaneous voices without rounding. */
    val offsetQuarters: Double? = null,
    val durationScale: Double = 1.0,

) {
    init {
        require(offsetQuarters == null || (offsetQuarters.isFinite() && offsetQuarters >= 0))
        require(durationScale.isFinite() && durationScale > 0)
        require(durationQuarters.isFinite() && durationQuarters > 0)
        require(notes.map { it.string }.distinct().size == notes.size) { "A beat cannot contain two frets on the same string" } }
    val isRest: Boolean get() = notes.isEmpty()
    val durationQuarters: Float get() = duration.quarters * (if (dotted) 1.5f else 1f) * durationScale.toFloat()
}
enum class Accidental { Natural, Sharp, Flat }
/** staffStep: diatonic offset from treble staff's bottom E4 line (0), not a MIDI offset.
 * Explicit spelling keeps enharmonic and key-signature decisions in the mapping layer. */
data class Note(
    val id: String, val midi: Int, val string: Int, val fret: Int,
    val staffStep: Int, val accidental: Accidental? = null, val effects: List<Effect> = emptyList(),
) { init { require(midi in 0..127); require(string > 0); require(fret >= 0); require(staffStep in -70..70) } }
sealed interface Effect {
    data class Bend(val semitones: Float = 1f) : Effect {
        init { require(semitones.isFinite() && semitones >= 0f) }
    }
    data object Slide : Effect
    data object HammerOn : Effect
    data object PullOff : Effect
    data object Vibrato : Effect
    data object PalmMute : Effect
}
