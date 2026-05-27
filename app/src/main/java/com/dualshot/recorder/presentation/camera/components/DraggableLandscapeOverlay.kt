package com.dualshot.recorder.presentation.camera.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.presentation.camera.PipOffset
import kotlin.math.roundToInt

/**
 * Draggable 16:9 landscape frame with optional live preview bitmap and status badges.
 * Preview bitmap is rendered on a background thread from the camera analysis stream.
 */
@Composable
fun DraggableLandscapeOverlay(
    previewBitmap: android.graphics.Bitmap?,
    recordingState: RecordingState,
    position: PipOffset,
    widthDp: Float,
    heightDp: Float,
    recordButtonBounds: Rect?,
    onDrag: (PipOffset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    var parentSize by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset(position.x, position.y)) }

    val animatedOffset by animateOffsetAsState(
        targetValue = Offset(position.x, position.y),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "overlaySnap"
    )

    val hasLivePreview = previewBitmap != null
    val isRecording = recordingState == RecordingState.RECORDING
    val isSaving = recordingState == RecordingState.SAVING

    val borderColor = when {
        isSaving -> Color.White.copy(alpha = 0.5f)
        isRecording -> Color(0xFFFF3B30)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                parentSize = Offset(size.width.toFloat(), size.height.toFloat())
                if (position.x == 0f && position.y == 0f) {
                    dragOffset = Offset(parentSize.x - widthPx - 16f, 120f)
                    onDrag(PipOffset(dragOffset.x, dragOffset.y))
                }
            }
    ) {
        val offsetX = if (dragOffset != Offset(position.x, position.y)) dragOffset.x else animatedOffset.x
        val offsetY = if (dragOffset != Offset(position.x, position.y)) dragOffset.y else animatedOffset.y

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(widthDp.dp, heightDp.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .then(
                    if (hasLivePreview) {
                        Modifier.background(Color.Black)
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A1A1E), Color(0xFF0D0D10))
                            )
                        )
                    }
                )
                .pointerInput(parentSize, recordButtonBounds) {
                    detectDragGestures(
                        onDragEnd = {
                            dragOffset = Offset(position.x, position.y)
                            onDragEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (dragOffset.x + dragAmount.x).coerceIn(0f, parentSize.x - widthPx)
                            val newY = (dragOffset.y + dragAmount.y).coerceIn(0f, parentSize.y - heightPx)
                            val candidate = Offset(newX, newY)
                            val blocked = recordButtonBounds?.let { bounds ->
                                candidate.x < bounds.right &&
                                    candidate.x + widthPx > bounds.left &&
                                    candidate.y < bounds.bottom &&
                                    candidate.y + heightPx > bounds.top
                            } ?: false
                            if (!blocked) {
                                dragOffset = candidate
                                onDrag(PipOffset(candidate.x, candidate.y))
                            }
                        }
                    )
                }
        ) {
            if (hasLivePreview) {
                PipBitmapImage(
                    previewBitmap = previewBitmap,
                    contentDescription = "Landscape preview",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.12f),
                        style = stroke,
                        topLeft = Offset(size.width * 0.08f, size.height * 0.12f),
                        size = androidx.compose.ui.geometry.Size(
                            size.width * 0.84f,
                            size.height * 0.84f
                        )
                    )
                }
            }

            if (hasLivePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "16:9",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    when {
                        isRecording -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFFFF3B30), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "REC",
                                    color = Color(0xFFFF3B30),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        isSaving -> {
                            Text(
                                text = "SAVING",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!hasLivePreview) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Horizontal",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isSaving -> "Starting preview…"
                                isRecording -> "Starting preview…"
                                else -> "Landscape output"
                            },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
