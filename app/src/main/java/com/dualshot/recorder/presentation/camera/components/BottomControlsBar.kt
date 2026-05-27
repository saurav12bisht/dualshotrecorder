package com.dualshot.recorder.presentation.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState

/**
 * Bottom controls above the record button: timer, storage, resolution, and FPS chips.
 */
@Composable
fun BottomControlsBar(
    recordingState: RecordingState,
    elapsedMs: Long,
    storageRemainingSeconds: Long,
    config: DualRecordingConfig,
    onResolutionSelected: (DualRecordingConfig.Resolution) -> Unit,
    onFpsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimerDisplay(elapsedMs = elapsedMs, recordingState = recordingState)
        StorageEstimate(remainingSeconds = storageRemainingSeconds)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DualRecordingConfig.Resolution.entries.forEach { res ->
                val label = if (res == DualRecordingConfig.Resolution.P4K) "4K" else "1080p"
                FilterChip(
                    selected = config.resolution == res,
                    onClick = { onResolutionSelected(res) },
                    label = { androidx.compose.material3.Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                FilterChip(
                    selected = config.fps == fps,
                    onClick = { onFpsSelected(fps) },
                    label = { androidx.compose.material3.Text("$fps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
    }
}
