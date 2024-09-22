import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalWindowInfo
import io.ktor.http.encodeURLPath
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.files.File
import org.w3c.files.FileReader

external fun registerDragAndDropListener(
    onDragOver: (DragEvent, Int, Int) -> Unit,
    onDragLeave: () -> Unit,
    onDrop: (baseDir: String, File) -> Unit,
)

external fun unregisterDragAndDropListener()

external class DragEvent(pageX: Int, pageY: Int) {
    val pageX: Int
    val pageY: Int
}

actual fun loadUrl(url: String) {
    window.open(url.encodeURLPath())
}

@Composable
actual fun Modifier.onDragAndDrop(
    onDragOver: (Float, Float) -> Unit,
    onDragLeave: () -> Unit,
    onSingleFileDropped: (String, ByteArray) -> Unit,
): Modifier = composed {
    DisposableEffect(LocalWindowInfo.current) {
        registerDragAndDropListener(
            onDragOver = { event, pageWidth, pageHeight ->
                onDragOver(event.pageX / pageWidth.toFloat(), event.pageY / pageHeight.toFloat())
            },
            onDragLeave = { onDragLeave() },
            onDrop = { baseDir, file ->
                FileReader().apply {
                    onload = {
                        onSingleFileDropped("$baseDir${file.name}", Int8Array(result as ArrayBuffer).toByteArray())
                    }
                    readAsArrayBuffer(file)
                }
            },
        )
        onDispose {
            unregisterDragAndDropListener()
        }
    }
    Modifier
}

fun ByteArray.copyInto(inputOffset: Int, output: Int8Array, outputOffset: Int, length: Int) {
    repeat(length) { index ->
        output[outputOffset + index] = get(inputOffset + index)
    }
}

fun ByteArray.toInt8Array(): Int8Array = Int8Array(size).also { copyInto(0, it, 0, size) }

fun Int8Array.copyInto(inputOffset: Int, output: ByteArray, outputOffset: Int, length: Int) {
    repeat(length) { index ->
        output[outputOffset + index] = get(inputOffset + index)
    }
}

fun Int8Array.toByteArray(): ByteArray = ByteArray(length).also { copyInto(0, it, 0, length) }