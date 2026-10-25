package dev.composetab.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Bounded reads for desktop files, Android content URIs and the optional download. */
internal fun InputStream.readGpBytes(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= TuxGuitarDecoder.MAX_FILE_BYTES) { "Guitar Pro file exceeds 16 MiB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal suspend fun downloadRequestedSong(): SampleSong = withContext(Dispatchers.IO) {
    val connection = URI("https://gprotab.net/en/tabs/metallica/nothing-else-matters?download").toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000; connection.readTimeout = 45_000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; ComposeTabSample/0.3)")
    try {
        require(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}. Save the supplied link and use Load GP file." }
        TuxGuitarDecoder.decode(connection.inputStream.use { it.readGpBytes() }, "Nothing Else Matters · user-requested gprotab.net file")
    } finally { connection.disconnect() }
}
