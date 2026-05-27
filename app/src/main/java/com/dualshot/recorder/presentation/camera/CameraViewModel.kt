package com.dualshot.recorder.presentation.camera

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.view.PreviewView
import com.dualshot.recorder.data.camera.CameraRepositoryImpl
import com.dualshot.recorder.data.settings.SettingsDataStore
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.domain.repository.CameraRepository
import com.dualshot.recorder.domain.repository.VideoRepository
import com.dualshot.recorder.domain.usecase.GetRecordingStateUseCase
import com.dualshot.recorder.domain.usecase.StartDualRecordingUseCase
import com.dualshot.recorder.domain.usecase.StopDualRecordingUseCase
import com.dualshot.recorder.service.CameraForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [CameraScreen] exposing [CameraUiState].
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val application: Application,
    private val startDualRecording: StartDualRecordingUseCase,
    private val stopDualRecording: StopDualRecordingUseCase,
    getRecordingState: GetRecordingStateUseCase,
    private val cameraRepository: CameraRepository,
    private val videoRepository: VideoRepository,
    private val settingsDataStore: SettingsDataStore,
    private val cameraRepositoryImpl: CameraRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** PiP frames — sampled so Compose does not recompose every background frame. */
    val pipPreviewBitmap: StateFlow<Bitmap?> = cameraRepositoryImpl.pipPreviewFlow
        .sample(100L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                _uiState.update {
                    it.copy(
                        config = config,
                        selectedMode = config.mode,
                        isDualLensSupported = cameraRepository.isDualLensSupported,
                        showDualLensWarning = config.mode == DualRecordingConfig.RecordingMode.DUAL_LENS &&
                            !cameraRepository.isDualLensSupported
                    )
                }
                updateStorageEstimate(config)
            }
        }
        viewModelScope.launch {
            settingsDataStore.pipCornerFlow.collect { corner ->
                _uiState.update { it.copy(pipCorner = corner) }
            }
        }
        viewModelScope.launch {
            combine(
                getRecordingState(),
                cameraRepository.elapsedTimeMs,
                cameraRepository.isFlashOn,
                cameraRepository.zoomRatio
            ) { state, elapsed, flash, zoom ->
                _uiState.value.copy(
                    recordingState = state,
                    elapsedTimeMs = elapsed,
                    isFlashOn = flash,
                    zoomRatio = zoom
                )
            }.collect { updated ->
                _uiState.value = updated
            }
        }
    }

    /**
     * Binds CameraX preview and YUV analysis for recording.
     */
    fun bindCamera(previewView: PreviewView) {
        if (!_uiState.value.hasCameraPermission || !_uiState.value.hasAudioPermission) return
        viewModelScope.launch {
            cameraRepositoryImpl.bindCamera(
                previewView = previewView,
                config = _uiState.value.config.copy(mode = _uiState.value.selectedMode)
            )
        }
    }

    /**
     * Updates permission grant state from the UI layer.
     */
    fun onPermissionsResult(camera: Boolean, audio: Boolean, notifications: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = camera,
                hasAudioPermission = audio,
                hasNotificationPermission = notifications,
                showPermissionRationale = !camera || !audio
            )
        }
    }

    /**
     * Starts dual recording and foreground service.
     */
    fun startRecording() {
        val config = _uiState.value.config.copy(mode = _uiState.value.selectedMode)
        viewModelScope.launch {
            startDualRecording(config).onSuccess {
                // Only start a foreground service when it's allowed (Android 13+ requires POST_NOTIFICATIONS).
                val canStartFgs = android.os.Build.VERSION.SDK_INT < 33 || _uiState.value.hasNotificationPermission
                if (canStartFgs) {
                    CameraForegroundService.start(application)
                }
                updateStorageEstimate(config)
            }.onFailure { error ->
                CameraForegroundService.stop(application)
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.ERROR,
                        errorMessage = error.message,
                        recordButtonShake = true
                    )
                }
            }
        }
    }

    /**
     * Stops dual recording and shows save snackbar on success.
     */
    fun stopRecording() {
        viewModelScope.launch {
            stopDualRecording().onSuccess { files ->
                CameraForegroundService.stop(application)
                _uiState.update {
                    it.copy(
                        showSaveSnackbar = true,
                        savedVideoUris = files.map { f -> f.uri },
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                CameraForegroundService.stop(application)
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.ERROR,
                        errorMessage = error.message,
                        recordButtonShake = true
                    )
                }
            }
        }
    }

    /**
     * Toggles torch / flash.
     */
    fun toggleFlash() {
        val enabled = !_uiState.value.isFlashOn
        cameraRepository.setFlashEnabled(enabled)
    }

    /**
     * Updates recording mode (single vs dual lens).
     */
    fun setRecordingMode(mode: DualRecordingConfig.RecordingMode) {
        viewModelScope.launch {
            settingsDataStore.setMode(mode)
            _uiState.update {
                it.copy(
                    selectedMode = mode,
                    showDualLensWarning = mode == DualRecordingConfig.RecordingMode.DUAL_LENS &&
                        !cameraRepository.isDualLensSupported
                )
            }
        }
    }

    /**
     * Updates resolution from UI chips.
     */
    fun setResolution(resolution: DualRecordingConfig.Resolution) {
        viewModelScope.launch { settingsDataStore.setResolution(resolution) }
    }

    /**
     * Updates FPS from UI chips.
     */
    fun setFps(fps: Int) {
        viewModelScope.launch { settingsDataStore.setFps(fps) }
    }

    /**
     * Updates PiP drag position.
     */
    fun updatePipPosition(offset: PipOffset) {
        _uiState.update { it.copy(pipPosition = offset) }
    }

    /**
     * Snaps PiP to nearest corner after drag ends.
     */
    fun snapPipToCorner(parentWidth: Float, parentHeight: Float, pipWidth: Float, pipHeight: Float) {
        val current = _uiState.value.pipPosition
        val corners = PipCorner.entries.map { corner ->
            corner to cornerOffset(corner, parentWidth, parentHeight, pipWidth, pipHeight)
        }
        val nearest = corners.minByOrNull { (_, offset) ->
            val dx = current.x - offset.x
            val dy = current.y - offset.y
            dx * dx + dy * dy
        }?.second ?: current
        val corner = nearestCorner(nearest, corners)
        _uiState.update { it.copy(pipPosition = nearest, pipCorner = corner) }
        viewModelScope.launch { settingsDataStore.setPipCorner(corner) }
    }

    /**
     * Dismisses save snackbar.
     */
    fun dismissSnackbar() {
        _uiState.update { it.copy(showSaveSnackbar = false) }
    }

    /**
     * Clears record button shake animation flag.
     */
    fun clearShake() {
        _uiState.update { it.copy(recordButtonShake = false) }
    }

    private fun updateStorageEstimate(config: DualRecordingConfig) {
        val seconds = videoRepository.estimateRemainingSeconds(
            config.bitRate.resolveBitRate(config.resolution)
        )
        _uiState.update { it.copy(storageRemainingSeconds = seconds) }
    }

    private fun cornerOffset(
        corner: PipCorner,
        parentW: Float,
        parentH: Float,
        pipW: Float,
        pipH: Float
    ): PipOffset = when (corner) {
        PipCorner.TOP_LEFT -> PipOffset(16f, 120f)
        PipCorner.TOP_RIGHT -> PipOffset(parentW - pipW - 16f, 120f)
        PipCorner.BOTTOM_LEFT -> PipOffset(16f, parentH - pipH - 200f)
        PipCorner.BOTTOM_RIGHT -> PipOffset(parentW - pipW - 16f, parentH - pipH - 200f)
    }

    private fun nearestCorner(
        offset: PipOffset,
        corners: List<Pair<PipCorner, PipOffset>>
    ): PipCorner = corners.minByOrNull { (_, o) ->
        val dx = offset.x - o.x
        val dy = offset.y - o.y
        dx * dx + dy * dy
    }?.first ?: PipCorner.TOP_RIGHT
}
