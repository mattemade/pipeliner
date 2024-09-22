var dragAndDropListener = null;

function registerDragAndDropListener(onDragOver, onDragLeave, onDrop) {
    dragAndDropListener = {
        onDragOver: onDragOver, 
        onDragLeave: onDragLeave, 
        onDrop: onDrop,
    };
}

function unregisterDragAndDropListener() {
    dragAndDropListener = null;
}

function dropHandler(ev) {
    ev.preventDefault();
    console.log(ev);
    if (ev.dataTransfer.items) {
        [...ev.dataTransfer.items].forEach((item, i) => {
            item.getAsFileSystemHandle().then((handle) => {
                uploadHandle("", handle);
            });
        });
      } else {
        [...ev.dataTransfer.files].forEach((file, i) => {
            dragAndDropListener?.onDrop("", file);
        });
      }
}

function uploadHandle(baseDir, handle) {
    if (handle.kind === "file") {
        handle.getFile().then((file) => {
            dragAndDropListener?.onDrop(baseDir, file);
        });
    } else if (handle.kind === "directory") {
        (async () => {
            for await (let value of handle.values()) {
                uploadHandle(baseDir + handle.name + "/", value);
            }
        })();
    }
}

function dragOverHandler(ev) {
    ev.preventDefault();
    const canvas = document.getElementById('ComposeTarget');
    const { width, height } = canvas.getBoundingClientRect();
    dragAndDropListener?.onDragOver(ev, width, height);
}

function dragLeaveHandler(ev) {
    ev.preventDefault();
    dragAndDropListener?.onDragLeave();
}
