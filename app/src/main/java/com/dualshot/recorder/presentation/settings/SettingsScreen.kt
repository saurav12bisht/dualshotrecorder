package com.dualshot.recorder.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.presentation.camera.PipCorner

/**
 * Settings screen for resolution, FPS, format, bit rate, and PiP defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingSection(title = "Resolution") {
                DualRecordingConfig.Resolution.entries.forEach { res ->
                    val label = if (res == DualRecordingConfig.Resolution.P4K) "4K" else "1080p"
                    FilterChip(
                        selected = config.resolution == res,
                        onClick = { viewModel.setResolution(res) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            SettingSection(title = "Frame rate") {
                listOf(24, 30, 60).forEach { fps ->
                    FilterChip(
                        selected = config.fps == fps,
                        onClick = { viewModel.setFps(fps) },
                        label = { Text("$fps fps") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            SettingSection(title = "Format") {
                DualRecordingConfig.VideoFormat.entries.forEach { format ->
                    FilterChip(
                        selected = config.format == format,
                        onClick = { viewModel.setFormat(format) },
                        label = { Text(format.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            SettingSection(title = "Bit rate") {
                DualRecordingConfig.BitRatePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = config.bitRate == preset,
                        onClick = { viewModel.setBitRate(preset) },
                        label = { Text(preset.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            SettingSection(title = "PiP default position") {
                PipCorner.entries.forEach { corner ->
                    FilterChip(
                        selected = uiState.pipCorner == corner,
                        onClick = { viewModel.setPipCorner(corner) },
                        label = { Text(corner.name.replace('_', ' ')) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Text(text = title, modifier = Modifier.padding(vertical = 12.dp))
    androidx.compose.foundation.layout.Row {
        content()
    }
}
