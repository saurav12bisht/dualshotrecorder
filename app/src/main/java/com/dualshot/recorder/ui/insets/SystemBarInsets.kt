package com.dualshot.recorder.ui.insets

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier

/**
 * Keeps content above the system navigation / gesture bar.
 */
fun Modifier.respectNavigationBar(): Modifier = navigationBarsPadding()

/**
 * Keeps content below the status bar and display cutout.
 */
fun Modifier.respectStatusBar(): Modifier = statusBarsPadding()

/**
 * Applies safe-drawing insets (status, navigation, and display cutouts).
 */
fun Modifier.respectSafeDrawing(): Modifier = safeDrawingPadding()
