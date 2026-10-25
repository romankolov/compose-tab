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
class DesktopMidiTransport : SampleTransport {
    private val mutableStatus = MutableStateFlow(TransportStatus())
    override val status = mutableStatus.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var polling: Job? = null
    private var sequencer: Sequencer? = null
    private var synth: Synthesizer? = null
    private val lock = Any()

    override suspend fun load(song: SampleSong) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            polling?.cancel(); sequencer?.close(); synth?.close()
            sequencer = null; synth = null
            val player = MidiSystem.getSequencer(false)
            player.open()
            try {
                if (System.getenv("COMPOSE_TAB_SILENT") != "1") {
                    val instrument = MidiSystem.getSynthesizer()
                    try { instrument.open(); player.transmitter.receiver = instrument.receiver; synth = instrument }
                    catch (_: Exception) { instrument.close() }
                }
                player.sequence = MidiSystem.getSequence(ByteArrayInputStream(song.midi))
                sequencer = player
                mutableStatus.value = TransportStatus(durationQuarters = player.tickLength.toDouble() / player.sequence.resolution,
                    audioAvailable = synth != null, message = if (synth != null) "General MIDI audio" else "No audio device — silent cursor preview")
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
    override fun play() = synchronized(lock) { sequencer?.let { if (it.tickPosition >= it.tickLength) it.tickPosition = 0; it.start() }; Unit }
    override fun pause() = synchronized(lock) { sequencer?.stop(); silence(); Unit }
    override fun stop() = synchronized(lock) { sequencer?.stop(); sequencer?.tickPosition = 0; silence(); Unit }
    override fun seek(quarter: Double) = synchronized(lock) {
        if (quarter.isFinite()) sequencer?.let { p -> p.tickPosition = (quarter.coerceAtLeast(0.0) * p.sequence.resolution).toLong().coerceAtMost(p.tickLength) }
        silence(); Unit
    }
    private fun silence() { synth?.channels?.forEach { it?.allSoundOff() } }
    override fun close() { polling?.cancel(); scope.cancel(); synchronized(lock) { sequencer?.close(); synth?.close(); sequencer = null; synth = null } }
}
