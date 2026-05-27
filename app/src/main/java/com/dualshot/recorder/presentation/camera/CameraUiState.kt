package com.dualshot.recorder.presentation.camera

import android.net.Uri
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState

/**
 * UI state for the main camera screen.
 */
data class CameraUiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsedTimeMs: Long = 0L,
    val storageRemainingSeconds: Long = 0L,
    val previewAspectRatio: Float = 9f / 16f,
    val pipPosition: PipOffset = PipOffset(),
    val pipSizeDp: PipSize = PipSize(),
    val pipCorner: PipCorner = PipCorner.TOP_RIGHT,
    val isFlashOn: Boolean = false,
    val zoomRatio: Float = 1f,
    val selectedMode: DualRecordingConfig.RecordingMode = DualRecordingConfig.RecordingMode.SINGLE_LENS,
    val config: DualRecordingConfig = DualRecordingConfig(),
    val isDualLensSupported: Boolean = false,
    val showDualLensWarning: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null,
    val recordButtonShake: Boolean = false,
    val savedVideoUris: List<Uri> = emptyList(),
    val showSaveSnackbar: Boolean = false
)

/**
 * PiP window offset in pixels from top-left of the parent.
 */
data class PipOffset(val x: Float = 0f, val y: Float = 0f)

/**
 * PiP window dimensions in dp.
 */
data class PipSize(val widthDp: Float = 160f, val heightDp: Float = 90f)

/**
 * Default corner for PiP snap positions.
 */
enum class PipCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}
