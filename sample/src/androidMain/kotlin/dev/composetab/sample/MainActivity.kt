package dev.composetab.sample

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private lateinit var services: AndroidSampleServices
    private var pendingFile: CompletableDeferred<Uri?>? = null
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingFile?.complete(uri); pendingFile = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        services = AndroidSampleServices(applicationContext) {
            check(pendingFile == null) { "A file picker is already open" }
            val result = CompletableDeferred<Uri?>()
            pendingFile = result
            try {
                // GP providers use many MIME types; inspect the header, not the extension.
                openDocument.launch(arrayOf("*/*"))
                result.await()
            } finally { if (pendingFile === result) pendingFile = null }
        }
        setContent { App(services = services) }
    }
    override fun onStop() {
        if (::services.isInitialized) services.transport.pause()
        super.onStop()
    }
    override fun onDestroy() {
        pendingFile?.cancel(); pendingFile = null
        if (::services.isInitialized) services.transport.close()
        super.onDestroy()
    }
}
