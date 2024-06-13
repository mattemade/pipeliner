import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pipeliner.composeapp.generated.resources.Res
import pipeliner.composeapp.generated.resources.document_svgrepo_com
import pipeliner.composeapp.generated.resources.folder_svgrepo_com
import pipeliner.composeapp.generated.resources.play_svgrepo_com

//val serverAddress = "http://127.0.0.1"

val serverAddress = "http://mattemade.net"
private var droppingTo: RemoteFile? = null

@Composable
@Preview
fun App(
    files: List<RemoteFile>,
    path: List<String>,
    navigateDown: (String) -> Unit,
    navigateUp: (times: Int) -> Unit,
    openFile: (String) -> Unit,
    deleteFile: (String) -> Unit,
    downloadDir: () -> Unit,
    onFileDragged: (draggedFileName: String, ByteArray) -> Unit,
    dialog: () -> DialogDefinition?,
    uploading: () -> Boolean,
) {
    val joinedPath = path.joinToString(separator = "/")
    MaterialTheme {
        var draggingToRelative by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        var composeCanvasSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier.fillMaxSize().onGloballyPositioned { composeCanvasSize = it.size }.onDragAndDrop(
                onDragOver = { x, y ->
                    draggingToRelative = x to y
                },
                onDragLeave = {
                    draggingToRelative = null
                    droppingTo = null
                },
                onSingleFileDropped = { filename, bytes ->
                    onFileDragged(filename, bytes)
                    draggingToRelative = null
                    droppingTo = null
                })
        ) {
            Column(Modifier.fillMaxSize()) {
                topButtons(joinedPath, path, navigateUp, downloadDir)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(files.size, key = { "$joinedPath ${files[it]}" }) {
                        val element = files[it]
                        file(
                            element,
                            joinedPath,
                            { draggingToRelative },
                            { composeCanvasSize },
                            onClick = {
                                if (element.isDirectory) {
                                    navigateDown(element.name)
                                } else {
                                    openFile(element.name)
                                }
                            },
                            onLaunch = { openFile("${element.name}/index.html") },
                            delete = { deleteFile(element.name) }
                        )
                    }
                }
            }
            dialog()?.let { dialog ->
                Box(
                    Modifier.fillMaxSize().background(Color.LightGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.background(Color.White).padding(16.dp),
                    ) {
                        Text(dialog.message, Modifier.height(32.dp).align(Alignment.CenterHorizontally))
                        Text(
                            dialog.optionA.name,
                            Modifier.height(32.dp).clickable {
                                dialog.optionA.action()
                            }.padding(horizontal = 16.dp)
                        )
                        Text(
                            dialog.optionB.name,
                            Modifier.height(32.dp).clickable {
                                dialog.optionB.action()
                            }.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            if (uploading()) {
                Box(
                    Modifier.fillMaxSize().background(Color.LightGray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.background(Color.White).padding(16.dp),
                    ) {
                        Text("Uploading...", Modifier.height(32.dp).align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun file(
    file: RemoteFile,
    path: String,
    draggingToRelative: () -> Pair<Float, Float>?,
    composeCanvasSize: () -> IntSize,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
    delete: () -> Unit,
) {
    var elementPosition by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var showContent by remember { mutableStateOf(false) }
    val isTargetForDragAndDrop: Boolean by remember {
        derivedStateOf {
            val draggingToRelative = draggingToRelative() ?: return@derivedStateOf false
            val composeCanvasSize = composeCanvasSize()
            val result = elementPosition.contains(
                Offset(
                    draggingToRelative.first * composeCanvasSize.width,
                    draggingToRelative.second * composeCanvasSize.height
                )
            )
            if (result) {
                droppingTo = file
            }
            return@derivedStateOf result && !file.isDirectory
        }
    }
    val backgroundColor = if (isTargetForDragAndDrop) Color.LightGray else Color.White
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(32.dp)
            .onPointerEvent(PointerEventType.Enter) {
                isFocused = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                isFocused = false
            }
            .background(backgroundColor)
            .clickable {
                onClick()
                focusManager.clearFocus()
            }
            .onGloballyPositioned { elementPosition = it.boundsInWindow() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isDirectory) {
            Image(painterResource(Res.drawable.folder_svgrepo_com), contentDescription = null, Modifier.size(24.dp))
        } else {
            val extention = file.name.takeLast(4)
            if (extention in imageExt) {
                AsyncImage(
                    model = "$serverAddress/files/$path/${file.name}",
                    filterQuality = FilterQuality.None,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else if (extention in audioExt) {
                Image(painterResource(Res.drawable.play_svgrepo_com), contentDescription = null, Modifier.size(24.dp))
            } else {
                Image(
                    painterResource(Res.drawable.document_svgrepo_com),
                    contentDescription = null,
                    Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(file.name)
        if (file.isLaunchable) {
            Spacer(Modifier.width(16.dp))
            Button(onClick = onLaunch) {
                Text("Play")
            }
        }
        if (isFocused) {
            Spacer(Modifier.width(16.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                "delete",
                Modifier.height(32.dp).clickable(onClick = delete).padding(horizontal = 8.dp)
            )
        }
    }
}

private val imageExt = setOf(".png", ".jpg")
private val audioExt = setOf(".wav", ".ogg", ".mp3")

@Composable
fun topButtons(
    joinedPath: String,
    path: List<String>,
    navigateUp: (times: Int) -> Unit,
    download: () -> Unit,
) {
    val size = path.size
    LazyRow(Modifier.padding(start = 16.dp)) {
        items(size + 1, key = { if (it == 0) "root" else "$joinedPath ${path[it - 1]}" }) { index ->
            //var backgroundColor by remember { mutableStateOf(Color.White) }
            Text(
                if (index == 0) "files/" else "${path[index - 1]}/",
                Modifier
                    .height(32.dp)
                    //.onFocusChanged { backgroundColor = if (it.isFocused) Color.LightGray else Color.White }
                    .clickable(/*enabled = index < size,*/ onClick = {
                        navigateUp(size - index)
                    })
            )
        }
        item {
            Text(
                "download",
                Modifier.padding(start = 16.dp).height(32.dp).clickable(onClick = download).padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
expect fun Modifier.onDragAndDrop(
    onDragOver: (Float, Float) -> Unit,
    onDragLeave: () -> Unit,
    onSingleFileDropped: (String, ByteArray) -> Unit,
): Modifier


expect fun loadUrl(url: String)