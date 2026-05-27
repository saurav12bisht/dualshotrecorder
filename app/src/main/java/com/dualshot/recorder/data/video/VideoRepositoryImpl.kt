package com.dualshot.recorder.data.video

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.dualshot.recorder.domain.model.VideoFile
import com.dualshot.recorder.domain.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VideoRepository] implementation using [MediaStoreWriter] and storage stats.
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreWriter: MediaStoreWriter
) : VideoRepository {

    override suspend fun saveToMediaStore(
        verticalTemp: File,
        horizontalTemp: File,
        extension: String
    ): Result<List<VideoFile>> {
        return mediaStoreWriter.writeDualVideos(verticalTemp, horizontalTemp, extension)
    }

    override fun estimateRemainingSeconds(bitRate: Int): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val availableBytes = stat.availableBytes
        // Two video streams + audio overhead (~2.2x single stream bit rate)
        val bytesPerSecond = (bitRate * 2.2 / 8).toLong().coerceAtLeast(1)
        return availableBytes / bytesPerSecond
    }

    override suspend fun listRecentVideos(limit: Int): List<VideoFile> {
        val results = mutableListOf<VideoFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${MediaStoreWriter.RELATIVE_PATH}%")
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sort
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val size = cursor.getLong(sizeCol)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                results.add(
                    VideoFile(
                        uri = uri,
                        displayName = name,
                        isVertical = name.contains("vertical", ignoreCase = true),
                        sizeBytes = size
                    )
                )
                count++
            }
        }
        return results
    }
}
