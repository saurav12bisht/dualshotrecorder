package com.dualshot.recorder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshot.recorder.data.settings.SettingsDataStore
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.presentation.camera.PipCorner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [SettingsScreen] persisting preferences via DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    data class SettingsUiState(
        val config: DualRecordingConfig = DualRecordingConfig(),
        val pipCorner: PipCorner = PipCorner.TOP_RIGHT
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.pipCornerFlow.collect { corner ->
                _uiState.update { it.copy(pipCorner = corner) }
            }
        }
    }

    /** Updates resolution preset. */
    fun setResolution(resolution: DualRecordingConfig.Resolution) {
        viewModelScope.launch { settingsDataStore.setResolution(resolution) }
    }

    /** Updates FPS. */
    fun setFps(fps: Int) {
        viewModelScope.launch { settingsDataStore.setFps(fps) }
    }

    /** Updates container format. */
    fun setFormat(format: DualRecordingConfig.VideoFormat) {
        viewModelScope.launch { settingsDataStore.setFormat(format) }
    }

    /** Updates bit rate preset. */
    fun setBitRate(preset: DualRecordingConfig.BitRatePreset) {
        viewModelScope.launch { settingsDataStore.setBitRate(preset) }
    }

    /** Updates default PiP corner. */
    fun setPipCorner(corner: PipCorner) {
        viewModelScope.launch { settingsDataStore.setPipCorner(corner) }
    }
}
