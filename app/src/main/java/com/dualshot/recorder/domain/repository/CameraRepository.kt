package com.dualshot.recorder.domain.repository

import androidx.camera.view.PreviewView
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.domain.model.VideoFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for camera binding, preview, and dual-stream recording control.
 */
interface CameraRepository {

    /** Current recording lifecycle state. */
    val recordingState: StateFlow<RecordingState>

    /** Elapsed recording time in milliseconds while [RecordingState.RECORDING]. */
    val elapsedTimeMs: StateFlow<Long>

    /** Whether the device supports ConcurrentCamera dual-lens mode. */
    val isDualLensSupported: Boolean

    /** Current torch (flash) enabled state. */
    val isFlashOn: StateFlow<Boolean>

    /** Current zoom ratio (1.0 = no zoom). */
    val zoomRatio: StateFlow<Float>

    /**
     * Binds CameraX preview and YUV ImageAnalysis for recording.
     * PiP preview is supplied separately via [CameraRepositoryImpl.pipPreviewFlow].
     */
    suspend fun bindCamera(
        previewView: PreviewView,
        config: DualRecordingConfig
    )

    /**
     * Starts dual encoding to temporary files; returns pair of temp paths.
     */
    suspend fun startRecording(config: DualRecordingConfig): Result<Unit>

    /**
     * Stops encoding and returns saved [VideoFile] entries after MediaStore write.
     */
    suspend fun stopRecording(): Result<List<VideoFile>>

    /** Toggles camera torch on/off. */
    fun setFlashEnabled(enabled: Boolean)

    /** Sets linear zoom ratio. */
    fun setZoomRatio(ratio: Float)

    /** Releases camera and encoder resources. */
    fun release()

    /** Observes thermal throttling hints to reduce bit rate. */
    fun observeThermalState(): Flow<Boolean>
}
