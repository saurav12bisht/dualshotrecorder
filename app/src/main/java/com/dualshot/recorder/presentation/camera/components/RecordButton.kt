package com.dualshot.recorder.presentation.camera.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dualshot.recorder.domain.model.RecordingState

/**
 * Instagram Reels–style record button with press morph and recording pulse.
 *
 * @param recordingState Current recording lifecycle state.
 * @param isPressed Whether the user is pressing the button.
 * @param shakeTrigger When true, plays error shake via scale animation.
 * @param onPressStart Called when press begins (start recording).
 * @param onPressEnd Called when press ends (stop recording).
 */
@Composable
fun RecordButton(
    recordingState: RecordingState,
    isPressed: Boolean,
    shakeTrigger: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = recordingState == RecordingState.RECORDING
    val ringColor = if (isRecording) Color.Red else Color.White
    val innerTargetScale = if (isPressed) 0.85f else 1f
    val innerScale by animateFloatAsState(
        targetValue = innerTargetScale,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "innerScale"
    )
    val cornerRadius by animateFloatAsState(
        targetValue = if (isRecording) 12f else 32f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "cornerRadius"
    )
    val shakeScale by animateFloatAsState(
        targetValue = if (shakeTrigger) 0.92f else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "shake"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(if (isRecording) pulseScale * shakeScale else shakeScale)
            .border(4.dp, ringColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        tryAwaitRelease()
                        onPressEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(innerScale)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(if (isRecording) Color.Red else Color.White)
        )
    }
}
