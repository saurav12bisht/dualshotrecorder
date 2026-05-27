package com.dualshot.recorder.data.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.dualshot.recorder.domain.model.VideoFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes encoded video files to MediaStore under Movies/DualShot/.
 */
@Singleton
class MediaStoreWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Copies temp files into MediaStore and triggers media scan.
     *
     * @param verticalTemp Temp file for 9:16 stream.
     * @param horizontalTemp Temp file for 16:9 stream.
     * @param extension File extension (mp4 or mov).
     */
    suspend fun writeDualVideos(
        verticalTemp: File,
        horizontalTemp: File,
        extension: String
    ): Result<List<VideoFile>> = runCatching {
        val timestamp = dateFormat.format(Date())
        val verticalName = "DualShot_${timestamp}_vertical.$extension"
        val horizontalName = "DualShot_${timestamp}_horizontal.$extension"
        val vertical = copyToMediaStore(verticalTemp, verticalName, isVertical = true)
        val horizontal = copyToMediaStore(horizontalTemp, horizontalName, isVertical = false)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(vertical.displayName, horizontal.displayName),
            arrayOf("video/*", "video/*"),
            null
        )
        verticalTemp.delete()
        horizontalTemp.delete()
        listOf(vertical, horizontal)
    }

    private fun copyToMediaStore(source: File, displayName: String, isVertical: Boolean): VideoFile {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/${
                if (displayName.endsWith(".mov")) "quicktime" else "mp4"
            }")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("Failed to create MediaStore entry for $displayName")
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: error("Failed to open output stream for $displayName")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        val size = source.length()
        return VideoFile(uri = uri, displayName = displayName, isVertical = isVertical, sizeBytes = size)
    }

    companion object {
        const val RELATIVE_PATH = "Movies/DualShot"
    }
}
