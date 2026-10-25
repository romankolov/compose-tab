package dev.composetab.sample

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.composetab.sample.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

class AndroidSampleServices(private val context: Context, private val pickFile: suspend () -> Uri?) : SampleServices {
    override val transport = AndroidMidiTransport(context)

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun initialSong(): SampleSong {
        val bytes = Res.readBytes("files/original-study.gp4")
        return withContext(Dispatchers.IO) { TuxGuitarDecoder.decode(bytes, "Bundled original Guitar Pro study") }
    }

    override suspend fun importGp(): SampleSong? {
        val uri = withContext(Dispatchers.Main.immediate) { pickFile() } ?: return null
        return withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Selected Guitar Pro file"
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open selected file" }.use { it.readGpBytes() }
            TuxGuitarDecoder.decode(bytes, name)
        }
    }

    override suspend fun requestedSong(): SampleSong = downloadRequestedSong()
}
