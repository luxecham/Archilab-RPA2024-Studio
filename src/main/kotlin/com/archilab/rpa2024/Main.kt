package com.archilab.rpa2024

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.archilab.rpa2024.ui.MainAppWindow
import com.archilab.rpa2024.ui.ProjectController
import com.archilab.rpa2024.ui.ProjectState
import java.awt.Dimension
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.io.File

fun main() = application {
    val project = remember { ProjectState() }
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { ProjectController(scope) }
    val windowState = rememberWindowState(width = 1460.dp, height = 960.dp)

    LaunchedEffect(Unit) {
        project.joints.firstOrNull()?.let { controller.loadSample(project, it) }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "ARCHILAB — Note de calcul RPA 2024",
        state = windowState
    ) {
        window.minimumSize = Dimension(1180, 760)
        DisposableEffect(window) {
            val listener = object : DropTargetAdapter() {
                override fun dragEnter(event: DropTargetDragEvent) {
                    if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        event.rejectDrag()
                    }
                }

                override fun dragOver(event: DropTargetDragEvent) {
                    if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        event.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        event.rejectDrag()
                    }
                }

                override fun drop(event: DropTargetDropEvent) {
                    try {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val transferable = event.transferable
                        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            val data = transferable.getTransferData(DataFlavor.javaFileListFlavor)
                            val files = (data as? List<*>)?.filterIsInstance<File>().orEmpty()
                            val joint = project.selected
                            val file = files.firstOrNull()
                            if (joint != null && file != null) {
                                controller.importFile(joint, file.toPath())
                                project.globalMessage =
                                    "Fichier déposé : ${file.name} — vérifiez l'affectation puis lancez l'analyse."
                            }
                            event.dropComplete(file != null)
                        } else {
                            event.dropComplete(false)
                        }
                    } catch (error: Throwable) {
                        event.dropComplete(false)
                        project.globalMessage = "Dépôt de fichier impossible : ${error.message}"
                    }
                }
            }
            val dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, listener, true)
            onDispose {
                dropTarget.isActive = false
            }
        }
        MainAppWindow(project, controller)
    }
}
