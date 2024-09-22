import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPath
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.synchronized
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val client = HttpClient()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val locationSplit = window.location.href.split("/")
    val baseUrl = locationSplit.take(3).joinToString(separator = "/")
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        var files by remember { mutableStateOf<List<RemoteFile>?>(null) }
        var pathStack by remember {
            mutableStateOf(locationSplit.drop(3).map { it.removePrefix("?") }.filter { it.isNotBlank() })
        }
        val coroutineScope = rememberCoroutineScope()

        fun joinedPath() = pathStack.joinToString(separator = "/")
        fun loadFiles() {
            files = emptyList()
            coroutineScope.updateFiles(path = joinedPath()) { files = it }
        }

        var dialog by remember { mutableStateOf<DialogDefinition?>(null) }
        var uploading by remember { mutableStateOf(false) }

        window.onpopstate = {
            pathStack = window.location.href.split("/").drop(3).map { it.removePrefix("?") }.filter { it.isNotBlank() }
            loadFiles()
        }

        App(
            files,
            pathStack.toList(),
            navigateDown = {
                pathStack += it
                loadFiles()
                window.history.pushState(data = null, title = "Pipeliner", url = "$baseUrl/?${joinedPath()}")
            },
            navigateUp = {
                pathStack = pathStack.dropLast(it)
                loadFiles()
                val joinedPath = joinedPath().let { if (it.isNotBlank()) "?$it" else it }
                window.history.pushState(data = null, title = "Pipeliner", url = "$baseUrl/$joinedPath")
            },
            openFile = {
                openFile("files/${joinedPath()}/$it")
            },
            deleteFile = {
                dialog = DialogDefinition(
                    "delete $it",
                    DialogOption("yes") {
                        dialog = null
                        coroutineScope.deleteFile("${joinedPath()}/$it") { loadFiles() }
                    },
                    DialogOption("no") { dialog = null },
                )
            },
            downloadDir = {
                coroutineScope.getZip(joinedPath()) {
                    openFile("files/zip/${joinedPath()}.zip")
                }
            },
            onFileDragged = { uploadFileName, bytes ->
                uploading = true
                coroutineScope.uploadFile("${joinedPath()}/$uploadFileName", bytes, {
                    uploading = false
                    loadFiles()
                })
            },
            dialog = { dialog },
            uploading = { uploading },
        )

        if (files == null) {
            loadFiles()
        }
    }
}

private fun openFile(filePath: String) {
    loadUrl("$serverAddress/$filePath")
}

private fun CoroutineScope.updateFiles(path: String = "", done: (List<RemoteFile>) -> Unit) {
    launch {
        val result =
            client.get(urlString = "$serverAddress/listFiles/$path".encodeURLPath()).bodyAsText().split(",").map {
                val (name, isDirectory, isLaunchable) = it.split("|")
                RemoteFile(name, isDirectory.toBoolean(), isLaunchable.toBoolean())
            }.sortedWith { left, right ->
                if (left.isDirectory && !right.isDirectory) {
                    -1
                } else if (!left.isDirectory && right.isDirectory) {
                    1
                } else left.name.compareTo(right.name)
            }
        done(result)
    }
}


private fun CoroutineScope.getZip(file: String, onCompleted: () -> Unit) {
    launch {
        client.get(urlString = "$serverAddress/getZip/$file".encodeURLPath())
        onCompleted()
    }
}

private fun CoroutineScope.deleteFile(file: String, onCompleted: () -> Unit) {
    launch {
        client.get(urlString = "$serverAddress/delete/$file".encodeURLPath())
        onCompleted()
    }
}

private val countdownLatch = atomic(0)

private fun CoroutineScope.uploadFile(
    uploadFileName: String,
    bytes: ByteArray,
    onCompleted: () -> Unit,
) {
    synchronized(countdownLatch) {
        countdownLatch.incrementAndGet()
    }
    launch {
        client.post(urlString = "$serverAddress/uploadFile".encodeURLPath()) {
            setBody(MultiPartFormDataContent(
                formData {
                    append("filename", uploadFileName)
                    append("document", bytes, headers = headers {
                        append(HttpHeaders.ContentDisposition, "filename=$uploadFileName")
                    }.build())
                }
            ))
        }
        val uploadsLeft = synchronized(countdownLatch) {
            countdownLatch.decrementAndGet()
        }
        if (uploadsLeft == 0) {
            delay(200)
            onCompleted()
        }
    }
}


