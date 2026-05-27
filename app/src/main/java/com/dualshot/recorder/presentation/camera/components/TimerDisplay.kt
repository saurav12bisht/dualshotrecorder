package com.dualshot.recorder.presentation.camera.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dualshot.recorder.domain.model.RecordingState

/**
 * Monospace recording timer shown while [RecordingState.RECORDING].
 *
 * @param elapsedMs Elapsed milliseconds since recording started.
 * @param recordingState Current state; hidden when [RecordingState.IDLE].
 */
@Composable
fun TimerDisplay(
    elapsedMs: Long,
    recordingState: RecordingState,
    modifier: Modifier = Modifier
) {
    if (recordingState == RecordingState.IDLE) return
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formatted = "%02d:%02d".format(minutes, seconds)
    Text(
        text = formatted,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            shadow = Shadow(Color.Black, blurRadius = 8f)
        )
    )
}
