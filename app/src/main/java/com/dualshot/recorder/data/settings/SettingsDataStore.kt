package com.dualshot.recorder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.presentation.camera.PipCorner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dualshot_settings")

/**
 * Persists user settings via DataStore Preferences.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val configFlow: Flow<DualRecordingConfig> = context.settingsDataStore.data.map { prefs ->
        DualRecordingConfig(
            resolution = DualRecordingConfig.Resolution.entries[
                prefs[KEY_RESOLUTION] ?: 0
            ],
            fps = prefs[KEY_FPS] ?: 30,
            format = DualRecordingConfig.VideoFormat.entries[
                prefs[KEY_FORMAT] ?: 0
            ],
            bitRate = DualRecordingConfig.BitRatePreset.entries[
                prefs[KEY_BITRATE] ?: 0
            ],
            mode = DualRecordingConfig.RecordingMode.entries[
                prefs[KEY_MODE] ?: 0
            ]
        )
    }

    val pipCornerFlow: Flow<PipCorner> = context.settingsDataStore.data.map { prefs ->
        PipCorner.entries[prefs[KEY_PIP_CORNER] ?: PipCorner.TOP_RIGHT.ordinal]
    }

    /**
     * Updates resolution preset index.
     */
    suspend fun setResolution(resolution: DualRecordingConfig.Resolution) {
        context.settingsDataStore.edit { it[KEY_RESOLUTION] = resolution.ordinal }
    }

    /**
     * Updates target FPS.
     */
    suspend fun setFps(fps: Int) {
        context.settingsDataStore.edit { it[KEY_FPS] = fps }
    }

    /**
     * Updates container format.
     */
    suspend fun setFormat(format: DualRecordingConfig.VideoFormat) {
        context.settingsDataStore.edit { it[KEY_FORMAT] = format.ordinal }
    }

    /**
     * Updates bit rate preset.
     */
    suspend fun setBitRate(preset: DualRecordingConfig.BitRatePreset) {
        context.settingsDataStore.edit { it[KEY_BITRATE] = preset.ordinal }
    }

    /**
     * Updates recording mode.
     */
    suspend fun setMode(mode: DualRecordingConfig.RecordingMode) {
        context.settingsDataStore.edit { it[KEY_MODE] = mode.ordinal }
    }

    /**
     * Updates default PiP corner.
     */
    suspend fun setPipCorner(corner: PipCorner) {
        context.settingsDataStore.edit { it[KEY_PIP_CORNER] = corner.ordinal }
    }

    companion object {
        private val KEY_RESOLUTION = intPreferencesKey("resolution")
        private val KEY_FPS = intPreferencesKey("fps")
        private val KEY_FORMAT = intPreferencesKey("format")
        private val KEY_BITRATE = intPreferencesKey("bitrate")
        private val KEY_MODE = intPreferencesKey("mode")
        private val KEY_PIP_CORNER = intPreferencesKey("pip_corner")
    }
}
