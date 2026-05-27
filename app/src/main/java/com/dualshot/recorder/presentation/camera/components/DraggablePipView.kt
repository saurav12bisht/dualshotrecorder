package com.dualshot.recorder.presentation.camera.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualshot.recorder.presentation.camera.PipOffset
import kotlin.math.roundToInt

/**
 * Draggable PiP window showing a 16:9 preview bitmap (from YUV analysis frames).
 */
@Composable
fun DraggablePipView(
    previewBitmap: android.graphics.Bitmap?,
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
        label = "pipSnap"
    )

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
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .background(Color.Black)
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
            PipBitmapImage(
                previewBitmap = previewBitmap,
                contentDescription = "16:9 preview",
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = "16:9",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
