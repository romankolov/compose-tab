package dev.composetab.sample

import app.tuxguitar.io.base.*
import app.tuxguitar.io.gtp.*
import app.tuxguitar.io.midi.MidiSongWriter
import app.tuxguitar.player.base.MidiRepeatController
import app.tuxguitar.song.factory.TGFactory
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGDuration
import dev.composetab.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Application adapter only. The score library never sees TuxGuitar objects or file bytes.
 * Both platforms decode the same bytes and export the same repeat/tempo-aware SMF. */
object TuxGuitarDecoder : ScoreAdapter<ByteArray> {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    override fun adapt(source: ByteArray): ScoreDocument = decode(source).document

    fun decode(bytes: ByteArray, sourceName: String = "Guitar Pro file"): SampleSong {
        require(bytes.size in 31..MAX_FILE_BYTES) { "Choose a GP1–GP5 file of at most 16 MiB" }
        // Classic Guitar Pro metadata uses Windows-1252, not UTF-8.
        val settings = GTPSettings().apply { charset = "windows-1252" }
        val readers = listOf(
            GP1InputStream(settings) to GP1InputStream.SUPPORTED_VERSIONS,
            GP2InputStream(settings) to GP2InputStream.SUPPORTED_VERSIONS,
            GP3InputStream(settings) to GP3InputStream.SUPPORTED_VERSIONS,
            GP4InputStream(settings) to GP4InputStream.SUPPORTED_VERSIONS,
            GP5InputStream(settings) to GP5InputStream.SUPPORTED_VERSIONS,
        )
        val reader = readers.firstOrNull { (_, versions) ->
            GTPFileFormatDetector(*versions).getFileFormatVersion(ByteArrayInputStream(bytes)) != null
        }?.first ?: error("Unsupported Guitar Pro format. Choose GP1–GP5 (.gtp, .gp3, .gp4, .gp5); GPX/GP6–8 are not supported by this sample.")
        val handle = TGSongReaderHandle().apply {
            factory = TGFactory(); context = TGSongStreamContext(); inputStream = ByteArrayInputStream(bytes)
        }
        try { reader.read(handle) }
        catch (e: TGFileFormatException) { throw IllegalArgumentException("Could not decode $sourceName: damaged or truncated Guitar Pro file", e) }
        val song = requireNotNull(handle.song) { "The decoder returned no song" }
        val manager = TGSongManager()
        val tracks = (0 until song.countTracks()).map { song.getTrack(it) }
            .filterNot { manager.isPercussionChannel(song, it.channelId) }.map { t ->
                val tuning = t.strings.map { it.value }
                val clef = if (tuning.size >= 6) Clef.Treble8vb else if (tuning.max() < 64) Clef.Bass else Clef.Treble
                val trackId = "t${t.number}"
                Track(trackId, t.name, tuning, (0 until t.countMeasures()).map { mi ->
                    val m = t.getMeasure(mi)
                    val measureId = "$trackId-m$mi"
                    Measure(measureId, m.number, TimeSignature(m.timeSignature.numerator, m.timeSignature.denominator.value),
                        beats = m.beats.flatMapIndexed { bi, b ->
                            (0 until 2).mapNotNull { vi ->
                                val v = b.getVoice(vi)
                                if (v.isEmpty) return@mapNotNull null
                                val d = v.duration
                                val duration = when (d.value) {
                                    1 -> Duration.Whole; 2 -> Duration.Half; 4 -> Duration.Quarter
                                    8 -> Duration.Eighth; 16 -> Duration.Sixteenth; else -> Duration.ThirtySecond
                                }
                                val beatId = "$measureId-b$bi-v$vi"
                                Beat(beatId, duration, d.isDotted, v.notes.mapIndexed { ni, n ->
                                    val midi = tuning[n.string - 1] + n.value + t.offset
                                    val written = midi + if (clef == Clef.Treble8vb) 12 else 0
                                    val pc = written % 12
                                    val steps = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
                                    Note("$beatId-n$ni", midi, n.string, n.value,
                                        (written / 12 - 1) * 7 + steps[pc] - if (clef == Clef.Bass) 18 else 30,
                                        if (pc in listOf(1, 3, 6, 8, 10)) Accidental.Sharp else null,
                                        buildList {
                                            val e = n.effect
                                            if (e.isBend) {
                                                val points = e.bend.points
                                                val peak = points.maxOfOrNull { it.value } ?: 0
                                                add(Effect.Bend(peak.toFloat(), release = (points.lastOrNull()?.value ?: peak) < peak,
                                                    hold = points.size > 2 && points.takeLast(2).all { it.value == peak }))
                                            }
                                            if (e.isDeadNote) add(Effect.DeadNote)
                                            if (e.isSlapping) add(Effect.Slap)
                                            if (e.isGhostNote) add(Effect.Ghost)
                                            if (e.isHarmonic) add(Effect.Harmonic(when (e.harmonic.type) {
                                                2 -> HarmonicKind.Artificial; 3 -> HarmonicKind.Tapped
                                                4 -> HarmonicKind.Pinch; 5 -> HarmonicKind.Semi; else -> HarmonicKind.Natural
                                            }))
                                            if (e.isHammer) {
                                                val next = (m.beats.drop(bi + 1) + (if (mi + 1 < t.countMeasures()) t.getMeasure(mi + 1).beats else emptyList()))
                                                    .firstNotNullOfOrNull { it.getVoice(vi).notes.firstOrNull { next -> next.string == n.string } }
                                                add(if (next != null && next.value < n.value) Effect.PullOff else Effect.HammerOn)
                                            }
                                            if (e.isSlide) add(Effect.Slide)
                                            if (e.isVibrato) add(Effect.Vibrato)
                                            if (e.isPalmMute) add(Effect.PalmMute)
                                        })
                                }, offsetQuarters = (b.start - m.start).toDouble() / TGDuration.QUARTER_TIME,
                                    durationScale = (d.time.toDouble() / TGDuration.QUARTER_TIME) / (duration.quarters * if (d.isDotted) 1.5 else 1.0),
                                    strum = when (b.stroke.direction) {
                                        1 -> StrumDirection.Up; -1 -> StrumDirection.Down; else -> null
                                    })
                            }
                        }, repeatStart = m.header.isRepeatOpen, repeatEnd = m.header.repeatClose > 0,
                        lengthQuarters = m.length.toDouble() / TGDuration.QUARTER_TIME)
                }, clef)
            }
        require(tracks.isNotEmpty()) { "This file has no pitched tracks to display" }
        val moments = mutableListOf<ScoreMoment>()
        // Use the exporter's repeat controller, capturing index/move BEFORE process(),
        // exactly as MidiSequenceParser does. Its SMF subtracts the initial quarter tick.
        val repeats = MidiRepeatController(song, -1, -1)
        var passes = 0
        while (!repeats.finished()) {
            require(++passes <= 100_000) { "Repeat expansion exceeds the sample safety limit" }
            val mi = repeats.index
            val move = repeats.repeatMove
            repeats.process()
            if (!repeats.shouldPlay()) continue
            val start = (song.getMeasureHeader(mi).start + move - TGDuration.QUARTER_TIME).toDouble() / TGDuration.QUARTER_TIME
            tracks.forEach { t ->
                val m = t.measures[mi]
                m.beats.forEach { b -> moments += ScoreMoment(t.id, m.id, b.id, start + (b.offsetQuarters ?: 0.0),
                    b.durationQuarters.toDouble(), b.notes.map { it.id }.toSet()) }
            }
        }
        val midi = ByteArrayOutputStream().also { output ->
            MidiSongWriter().write(TGSongWriterHandle().apply {
                this.song = song; factory = handle.factory; context = TGSongStreamContext(); outputStream = output
            })
        }.toByteArray()
        return SampleSong(ScoreDocument(ScoreData(song.name, song.artist.ifBlank { song.author }, tracks), ScoreTimeline(moments)), midi,
            listOf("TuxGuitar Java decoder · GP1–GP5", "Percussion plays in MIDI but is hidden from the pitched score."), sourceName)
    }
}
