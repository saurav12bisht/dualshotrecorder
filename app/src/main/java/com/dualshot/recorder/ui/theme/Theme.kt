package com.dualshot.recorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = WhiteAccent,
    secondary = RedRecording,
    background = BlackBackground,
    surface = BlackBackground
)

/**
 * DualShot Material3 dark theme (camera-first UI).
 */
@Composable
fun DualShotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
