package com.dualshot.recorder.domain.repository

import com.dualshot.recorder.domain.model.VideoFile
import java.io.File

/**
 * Abstraction for persisting encoded video files via MediaStore.
 */
interface VideoRepository {

    /**
     * Writes encoded temp files to Movies/DualShot/ and returns content URIs.
     */
    suspend fun saveToMediaStore(
        verticalTemp: File,
        horizontalTemp: File,
        extension: String
    ): Result<List<VideoFile>>

    /**
     * Estimates remaining recording seconds from available storage and bit rate.
     */
    fun estimateRemainingSeconds(bitRate: Int): Long

    /**
     * Lists recent DualShot videos from MediaStore.
     */
    suspend fun listRecentVideos(limit: Int = 50): List<VideoFile>
}
