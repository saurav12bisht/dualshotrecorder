package com.dualshot.recorder.presentation.camera.components

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows a PiP [Bitmap] without calling [ImageView.setImageBitmap] unless the reference changed.
 */
@Composable
internal fun PipBitmapImage(
    previewBitmap: Bitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lastBitmap = remember { mutableStateOf<Bitmap?>(null) }
    AndroidView(
        factory = {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                this.contentDescription = contentDescription
            }
        },
        update = { view ->
            if (previewBitmap !== lastBitmap.value) {
                view.setImageBitmap(previewBitmap)
                lastBitmap.value = previewBitmap
            }
        },
        modifier = modifier
    )
}
