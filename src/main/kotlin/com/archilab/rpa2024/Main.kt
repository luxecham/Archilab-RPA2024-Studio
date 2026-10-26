package com.archilab.rpa2024

import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.archilab.rpa2024.ui.MainAppWindow

fun main() = application {
    val windowState = rememberWindowState(width = 1360.dp, height = 860.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "RPA 2024 Engineering Suite - Auto-Discovery ETABS & Note de Calcul CTC (ArchiLab)"
    ) {
        MaterialTheme {
            MainAppWindow()
        }
    }
}
