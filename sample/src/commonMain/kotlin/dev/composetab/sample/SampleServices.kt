package dev.composetab.sample

import kotlinx.coroutines.flow.StateFlow

/** Sample-only platform player. Hosts can replace this entire service with their own player. */
data class TransportStatus(
    val playing: Boolean = false, val quarter: Double = 0.0, val durationQuarters: Double = 0.0,
    val audioAvailable: Boolean = false, val message: String = "Not loaded",
)
interface SampleTransport {
    val status: StateFlow<TransportStatus>
    suspend fun load(song: SampleSong)
    fun play()
    fun pause()
    fun stop()
    fun seek(quarter: Double)
    fun close()
}
interface SampleServices {
    val transport: SampleTransport
    suspend fun initialSong(): SampleSong
    suspend fun importGp(): SampleSong?
    suspend fun requestedSong(): SampleSong
}
