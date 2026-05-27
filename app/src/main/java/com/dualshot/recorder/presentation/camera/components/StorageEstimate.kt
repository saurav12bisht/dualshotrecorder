package com.dualshot.recorder.presentation.camera.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * Displays estimated remaining recording time based on free storage.
 *
 * @param remainingSeconds Estimated seconds of dual recording remaining.
 */
@Composable
fun StorageEstimate(
    remainingSeconds: Long,
    modifier: Modifier = Modifier
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Text(
        text = "~${minutes}m ${seconds}s remaining",
        color = Color.Gray,
        fontSize = 12.sp,
        modifier = modifier
    )
}
