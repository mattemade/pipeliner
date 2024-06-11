import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.onDragAndDrop(
    onDragOver: (Int, Int) -> Unit,
    onDragExit: () -> Unit,
    onSingleFileDropped: (String, ByteArray) -> Unit
): Modifier {
    return this
}