package com.dualshot.recorder.domain.model

import android.net.Uri

/**
 * Metadata for a saved video file in MediaStore.
 *
 * @property uri Content URI of the saved file.
 * @property displayName File name shown in the gallery.
 * @property isVertical True if this is the 9:16 vertical stream.
 * @property sizeBytes File size in bytes.
 */
data class VideoFile(
    val uri: Uri,
    val displayName: String,
    val isVertical: Boolean,
    val sizeBytes: Long = 0L
)
