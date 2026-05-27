package com.dualshot.recorder.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualshot.recorder.domain.model.DualRecordingConfig

/**
 * Top control bar with flash, mode chip, and settings navigation.
 */
@Composable
fun TopControlsBar(
    isFlashOn: Boolean,
    selectedMode: DualRecordingConfig.RecordingMode,
    onFlashToggle: () -> Unit,
    onModeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "Flash",
            tint = if (isFlashOn) Color.Yellow else Color.White,
            modifier = Modifier
                .clickable(onClick = onFlashToggle)
                .padding(8.dp)
        )
        Text(
            text = when (selectedMode) {
                DualRecordingConfig.RecordingMode.DUAL_LENS -> "Dual Lens"
                DualRecordingConfig.RecordingMode.SINGLE_LENS -> "Single Lens"
            },
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .clickable(onClick = onModeClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = androidx.compose.ui.text.TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f))
        )
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = Color.White,
            modifier = Modifier
                .clickable(onClick = onSettingsClick)
                .padding(8.dp)
        )
    }
}
