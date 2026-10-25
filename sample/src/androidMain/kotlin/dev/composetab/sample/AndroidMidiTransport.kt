package dev.composetab.sample

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Native Android SMF/General MIDI playback (no JavaSound and no silent fake clock).
 * All MediaPlayer operations run on the main looper. Poll only its actual position.
 * Playback is foreground-only; MainActivity pauses it onStop and releases onDestroy. */
class AndroidMidiTransport(private val context: Context) : SampleTransport {
    private val mutableStatus = MutableStateFlow(TransportStatus())
    override val status = mutableStatus.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var midiFile: File? = null
    private var clock: MidiTempoMap? = null
    private var ready = false
    private var closed = false
    private var seeking = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> if (change < 0) pause() }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            updatePosition()
            handler.postDelayed(this, 32)
        }
    }
    init { handler.post(poll) }
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
    private fun updatePosition() {
        val p = player ?: return
        if (!ready || seeking) return
        mutableStatus.value = mutableStatus.value.copy(playing = p.isPlaying,
            quarter = (clock?.quarterAt(p.currentPosition.toDouble()) ?: 0.0).coerceIn(0.0, mutableStatus.value.durationQuarters))
    }
    private fun releasePlayer() {
        ready = false; seeking = false
        player?.release(); player = null
        midiFile?.delete(); midiFile = null
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusListener)
    }
    override suspend fun load(song: SampleSong) = withContext(Dispatchers.Main.immediate) {
        check(!closed) { "Player is closed" }
        // Parse first, so a corrupt export does not destroy a working player.
        val timing = withContext(Dispatchers.Default) { MidiTempoMap.parse(song.midi) }
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("compose-tab-", ".mid", context.cacheDir).apply { writeBytes(song.midi) }
        }
        releasePlayer(); clock = timing; midiFile = file
        mutableStatus.value = TransportStatus(durationQuarters = song.document.timeline.durationQuarters, message = "Preparing Android MIDI audio…")
        val p = MediaPlayer(); player = p
        // Keep the decoded score usable even if a device has no MIDI codec.
        val error = suspendCancellableCoroutine<String?> { continuation ->
            p.setOnErrorListener { _, what, extra ->
                ready = false
                val message = "Android MIDI audio unavailable ($what/$extra)"
                mutableStatus.value = mutableStatus.value.copy(playing = false, audioAvailable = false, message = message)
                if (continuation.isActive) continuation.resume(message)
                true
            }
            p.setOnPreparedListener {
                ready = true
                if (continuation.isActive) continuation.resume(null)
            }
            p.setOnCompletionListener { updatePosition(); mutableStatus.value = mutableStatus.value.copy(playing = false) }
            p.setOnSeekCompleteListener { seeking = false; updatePosition() }
            continuation.invokeOnCancellation { onMain { if (player === p) releasePlayer() } }
            try {
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                p.setDataSource(file.absolutePath)
                p.prepareAsync()
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(e.message ?: "Android MIDI audio unavailable")
            }
        }
        if (error != null) {
            releasePlayer()
            mutableStatus.value = mutableStatus.value.copy(audioAvailable = false, message = error)
        } else {
            mutableStatus.value = mutableStatus.value.copy(audioAvailable = true, message = "Android General MIDI · native player clock")
        }
    }
    @Suppress("DEPRECATION") // Compatible audio-focus API for the sample's minSdk 24.
    override fun play() = onMain {
        val p = player
        if (ready && p != null) {
            if (audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (p.currentPosition >= p.duration) seek(0.0)
                p.start(); updatePosition()
            } else mutableStatus.value = mutableStatus.value.copy(message = "Audio focus unavailable; try Play again")
        }
    }
    override fun pause() = onMain {
        if (ready) { player?.takeIf { it.isPlaying }?.pause(); updatePosition() }
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusListener)
    }
    override fun stop() = onMain { pause(); seek(0.0) }
    override fun seek(quarter: Double) = onMain {
        val p = player
        if (ready && p != null && quarter.isFinite()) {
            val q = quarter.coerceIn(0.0, mutableStatus.value.durationQuarters)
            seeking = true
            @Suppress("DEPRECATION")
            p.seekTo((clock?.millisecondsAt(q) ?: 0.0).toInt().coerceIn(0, p.duration))
        }
    }
    override fun close() = onMain { closed = true; handler.removeCallbacks(poll); releasePlayer(); clock = null }
}
