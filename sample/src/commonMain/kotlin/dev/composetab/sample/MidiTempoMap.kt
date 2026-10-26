package dev.composetab.sample

/** Converts native Android MediaPlayer milliseconds to musical quarters. Tempo changes
 * come from the exported SMF, not an assumed BPM or an independently advancing timer. */
internal class MidiTempoMap private constructor(private val segments: List<Segment>) {
    private data class Segment(val quarter: Double, val milliseconds: Double, val msPerQuarter: Double)
    fun quarterAt(milliseconds: Double): Double {
        val ms = milliseconds.coerceAtLeast(0.0)
        val s = segments.lastOrNull { it.milliseconds <= ms } ?: segments.first()
        return s.quarter + (ms - s.milliseconds) / s.msPerQuarter
    }
    fun millisecondsAt(quarter: Double): Double {
        val q = quarter.coerceAtLeast(0.0)
        val s = segments.lastOrNull { it.quarter <= q } ?: segments.first()
        return s.milliseconds + (q - s.quarter) * s.msPerQuarter
    }
    companion object {
        fun parse(bytes: ByteArray): MidiTempoMap {
            var position = 0
            fun byte(): Int { require(position < bytes.size) { "Truncated MIDI" }; return bytes[position++].toInt() and 255 }
            fun integer(count: Int): Int { var n = 0; repeat(count) { n = (n shl 8) or byte() }; return n }
            fun skip(count: Int) { require(count >= 0 && count <= bytes.size - position); position += count }
            fun variable(): Int {
                var n = 0
                repeat(4) { val b = byte(); n = (n shl 7) or (b and 127); if (b < 128) return n }
                error("Invalid MIDI variable-length value")
            }
            require(integer(4) == 0x4d546864) { "Missing MIDI header" }
            val headerLength = integer(4); require(headerLength >= 6)
            val format = integer(2); require(format in 0..1) { "Only simultaneous-track SMF is supported" }
            val tracks = integer(2); val resolution = integer(2)
            require(resolution in 1..32767) { "SMPTE MIDI timing is not supported" }
            skip(headerLength - 6)
            val tempos = mutableListOf(0L to 500_000)
            repeat(tracks) {
                require(integer(4) == 0x4d54726b) { "Missing MIDI track" }
                val size = integer(4); require(size >= 0 && size <= bytes.size - position)
                val end = position + size
                var tick = 0L; var running = 0
                while (position < end) {
                    tick += variable()
                    var status = byte()
                    if (status < 128) { require(running in 0x80..0xef); position--; status = running }
                    when {
                        status == 0xff -> {
                            val type = byte(); val length = variable()
                            if (type == 0x51 && length == 3) {
                                val tempo = integer(3); require(tempo > 0); tempos += tick to tempo
                            } else skip(length)
                        }
                        status == 0xf0 || status == 0xf7 -> { running = 0; skip(variable()) }
                        status in 0x80..0xef -> { running = status; skip(if ((status and 0xf0) in listOf(0xc0, 0xd0)) 1 else 2) }
                        else -> error("Unsupported MIDI event")
                    }
                    require(position <= end) { "MIDI event overruns track" }
                }
            }
            val segments = mutableListOf(Segment(0.0, 0.0, 500.0))
            tempos.sortedBy { it.first }.forEach { (tick, tempo) ->
                val q = tick.toDouble() / resolution
                val previous = segments.last()
                val ms = previous.milliseconds + (q - previous.quarter) * previous.msPerQuarter
                segments += Segment(q, ms, tempo / 1000.0)
            }
            return MidiTempoMap(segments)
        }
    }
}
