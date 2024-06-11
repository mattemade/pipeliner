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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val client = HttpClient()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        var files by remember { mutableStateOf(emptyList<RemoteFile>()) }
        var pathStack by remember { mutableStateOf(emptyList<String>()) }
        val coroutineScope = rememberCoroutineScope()

        fun joinedPath() = pathStack.joinToString(separator = "/")
        fun loadFiles() {
            coroutineScope.updateFiles(path = joinedPath()) { files = it }
        }

        var uploadFileNameSelector by remember { mutableStateOf<UploadFileNameSelector?>(null) }
        var uploading by remember { mutableStateOf(false) }

        App(
            files,
            pathStack.toList(),
            navigateDown = {
                pathStack += it
                loadFiles()
            },
            navigateUp = {
                pathStack = pathStack.dropLast(it)
                loadFiles()
            },
            openFile = {
                openFile("files/${joinedPath()}/$it")
            },
            onFileDragged = { replacingFileName, uploadFileName, bytes ->
                if (replacingFileName != null) {
                    uploadFileNameSelector = UploadFileNameSelector(replacingFileName, uploadFileName, bytes)
                } else {
                    uploadFileNameSelector = null
                    uploading = true
                    coroutineScope.uploadFile("${joinedPath()}/$uploadFileName", bytes, {
                        uploading = false
                        loadFiles()
                    })
                }
            },
            uploadFileNameSelector = { uploadFileNameSelector },
            uploading = { uploading },
        )

        if (files.isEmpty() && pathStack.isEmpty()) {
            loadFiles()
        }
    }
}

private fun openFile(filePath: String) {
    loadUrl("$serverAddress/$filePath")
}

private fun CoroutineScope.updateFiles(path: String = "", done: (List<RemoteFile>) -> Unit) {
    launch {
        val result = client.get(urlString = "$serverAddress/listFiles/$path").bodyAsText().split(",").map {
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

private fun CoroutineScope.uploadFile(
    uploadFileName: String,
    bytes: ByteArray,
    onCompleted: () -> Unit,
) {
    launch {
        client.post(urlString = "$serverAddress/uploadFile") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("filename", uploadFileName)
                    append("document", bytes, headers = headers {
                        append(HttpHeaders.ContentDisposition, "filename=$uploadFileName")
                    }.build())
                }
            ))
        }
        onCompleted()
    }
}


