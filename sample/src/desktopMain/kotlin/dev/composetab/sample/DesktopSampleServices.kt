package dev.composetab.sample

import dev.composetab.sample.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import javax.sound.midi.Synthesizer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Sequence
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.net.HttpURLConnection
import java.net.URI

class DesktopSampleServices(private val input: String? = null) : SampleServices {
    override val transport = DesktopMidiTransport()

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun initialSong(): SampleSong {
        val supplied = input?.let(::File) ?: File("sample/local/nothing-else-matters.gp4").takeIf { it.isFile }
        if (supplied != null) return decode(supplied)
        val temporary = withContext(Dispatchers.IO) { File.createTempFile("compose-tab-study", ".gp4") }
        return try {
            temporary.writeBytes(Res.readBytes("files/original-study.gp4"))
            decode(temporary, "Bundled original GP4 study")
        } finally { temporary.delete() }
    }

    override suspend fun importGp4(): SampleSong? {
        val file = withContext(Dispatchers.Main) {
            val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Guitar Pro 4", "gp4") }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
        return file?.let { decode(it) }
    }

    override suspend fun requestedSong(): SampleSong = withContext(Dispatchers.IO) {
        val file = File.createTempFile("nothing-else-matters", ".gp4")
        try {
            val connection = URI("https://gprotab.net/en/tabs/metallica/nothing-else-matters?download").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15000; connection.readTimeout = 45000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; ComposeTabSample/0.3)")
            try {
                require(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}. Save the supplied link and use Open GP4." }
                connection.inputStream.use { stream ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0
                        while (true) { val count = stream.read(buffer); if (count < 0) break; total += count
                            require(total <= 16 * 1024 * 1024) { "Download exceeds 16 MiB" }; output.write(buffer, 0, count) }
                    }
                }
            } finally { connection.disconnect() }
            decode(file, "Nothing Else Matters · user-requested gprotab.net file")
        } finally { file.delete() }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun decode(file: File, label: String = file.name): SampleSong {
        require(file.length() in 1..(16L * 1024 * 1024)) { "Choose a GP4 file of at most 16 MiB" }
        val script = Res.readBytes("files/decode_gp4.py")
        return withContext(Dispatchers.IO) {
            val directory = Files.createTempDirectory("compose-tab-gp4").toFile()
            var process: Process? = null
            try {
                val tool = File(directory, "decode.py").apply { writeBytes(script) }
                val output = File(directory, "score.json")
                val errors = File(directory, "errors.txt")
                val python = System.getenv("COMPOSE_TAB_PYTHON") ?: File(".venv/bin/python").takeIf { it.isFile }?.absolutePath ?: "python3"
                val started = ProcessBuilder(python, tool.absolutePath, file.absolutePath).redirectOutput(output).redirectError(errors).start()
                process = started
                if (!started.waitFor(45, TimeUnit.SECONDS)) error("GP4 decoder timed out")
                check(started.exitValue() == 0) { errors.readText().take(2000) + "\nInstall sample/tools/requirements.txt; set COMPOSE_TAB_PYTHON to your Python 3.10+ executable." }
                require(output.length() <= 64L * 1024 * 1024) { "Decoded score exceeds sample memory limit" }
                Gp4DocumentAdapter.parse(output.readText(), label)
            } finally { process?.destroyForcibly(); directory.deleteRecursively() }
        }
    }
}

/** General MIDI reference, not part of the library. Uses the sequencer's actual tick
 * position (including tempo changes), not a separate coroutine approximation of audio. */
class DesktopMidiTransport(private val enableAudio: Boolean = System.getenv("COMPOSE_TAB_SILENT") != "1") : SampleTransport {
    private val mutableStatus = MutableStateFlow(TransportStatus())
    override val status = mutableStatus.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var polling: Job? = null
    private var sequencer: Sequencer? = null
    private var synth: Synthesizer? = null
    private val lock = Any()
    private data class HeldNote(val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private var heldNotes: List<HeldNote> = emptyList()
    private var programs: Map<Int, Int> = emptyMap()
    private fun indexNotes(sequence: Sequence) {
        val pending = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Long, Int>>>()
        val notes = mutableListOf<HeldNote>()
        val patches = mutableMapOf<Int, Int>()
        val events = sequence.tracks.flatMap { track -> (0 until track.size()).map { track[it] } }.sortedBy { it.tick }
        events.forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.PROGRAM_CHANGE) patches[message.channel] = message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) pending.getOrPut(key) { mutableListOf() }.add(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)) {
                val active = pending[key]
                if (!active.isNullOrEmpty()) {
                    val start = active.removeAt(0)
                    notes += HeldNote(key.first, key.second, start.second, start.first, event.tick)
                }
            }
        }
        heldNotes = notes
        programs = patches
    }
    private fun chaseHeldNotes(tick: Long) {
        val channels = synth?.channels ?: return
        programs.forEach { (channel, patch) -> channels.getOrNull(channel)?.programChange(patch) }
        heldNotes.filter { it.start < tick && tick < it.end }.forEach { note -> channels.getOrNull(note.channel)?.noteOn(note.pitch, note.velocity) }
    }

    override suspend fun load(song: SampleSong) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            polling?.cancel(); sequencer?.close(); synth?.close()
            sequencer = null; synth = null
            val player = MidiSystem.getSequencer(false)
            player.open()
            try {
                if (enableAudio) {
                    val instrument = MidiSystem.getSynthesizer()
                    try { instrument.open(); player.transmitter.receiver = instrument.receiver; synth = instrument }
                    catch (_: Exception) { instrument.close() }
                }
                player.sequence = MidiSystem.getSequence(ByteArrayInputStream(song.midi))
                indexNotes(player.sequence)
                sequencer = player
                mutableStatus.value = TransportStatus(durationQuarters = player.tickLength.toDouble() / player.sequence.resolution,
                    audioAvailable = synth != null, message = if (synth != null) "General MIDI audio" else "Silent cursor preview — audio disabled or unavailable")
            } catch (error: Exception) { player.close(); throw error }
        }
        polling = scope.launch {
            while (isActive) {
                synchronized(lock) {
                    sequencer?.let { p ->
                        mutableStatus.value = mutableStatus.value.copy(playing = p.isRunning,
                            quarter = p.tickPosition.toDouble() / p.sequence.resolution)
                    }
                }
                delay(16)
            }
        }
    }
    override fun play() = synchronized(lock) {
        sequencer?.let { if (it.tickPosition >= it.tickLength) it.tickPosition = 0; silence(); chaseHeldNotes(it.tickPosition); it.start() }
        Unit
    }
    override fun pause() = synchronized(lock) { sequencer?.stop(); silence(); Unit }
    override fun stop() = synchronized(lock) { sequencer?.stop(); sequencer?.tickPosition = 0; silence(); Unit }
    override fun seek(quarter: Double) = synchronized(lock) {
        if (quarter.isFinite()) sequencer?.let { p ->
            val running = p.isRunning
            p.stop(); silence()
            p.tickPosition = (quarter.coerceAtLeast(0.0) * p.sequence.resolution).toLong().coerceAtMost(p.tickLength)
            if (running) { chaseHeldNotes(p.tickPosition); p.start() }
        }
        Unit
    }
    private fun silence() { synth?.channels?.forEach { it?.allSoundOff() } }
    override fun close() { polling?.cancel(); scope.cancel(); synchronized(lock) { sequencer?.close(); synth?.close(); sequencer = null; synth = null } }
}
