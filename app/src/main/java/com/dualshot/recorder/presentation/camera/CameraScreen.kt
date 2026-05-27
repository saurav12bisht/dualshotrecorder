package com.dualshot.recorder.presentation.camera

import android.Manifest
import android.content.Intent
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.presentation.camera.components.BottomControlsBar
import com.dualshot.recorder.presentation.camera.components.DraggableLandscapeOverlay
import com.dualshot.recorder.presentation.camera.components.DraggablePipView
import com.dualshot.recorder.presentation.camera.components.RecordButton
import com.dualshot.recorder.presentation.camera.components.TopControlsBar
import com.dualshot.recorder.ui.insets.respectNavigationBar
import kotlinx.coroutines.delay

/**
 * Main camera screen with vertical preview, draggable 16:9 PiP, and Reels-style controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onNavigateSettings: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pipPreviewBitmap by viewModel.pipPreviewBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showModeSheet by remember { mutableStateOf(false) }
    var recordButtonRect by remember { mutableStateOf<Rect?>(null) }
    var isPressingRecord by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val camera = result[Manifest.permission.CAMERA] == true
        val audio = result[Manifest.permission.RECORD_AUDIO] == true
        val notifications = if (Build.VERSION.SDK_INT >= 33) {
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }
        viewModel.onPermissionsResult(camera, audio, notifications)
        showRationale = !camera || !audio
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (cameraGranted && audioGranted && notificationsGranted) {
            viewModel.onPermissionsResult(true, true, true)
        } else {
            val perms = buildList {
                add(Manifest.permission.CAMERA)
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            permissionLauncher.launch(
                perms
            )
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    LaunchedEffect(
        uiState.hasCameraPermission,
        uiState.hasAudioPermission,
        uiState.config,
        uiState.selectedMode
    ) {
        if (uiState.hasCameraPermission && uiState.hasAudioPermission) {
            viewModel.bindCamera(previewView)
        }
    }

    // "Crossfade transition" without swapping the underlying AndroidView instance.
    val previewAlphaTarget = if (previewVisible) 1f else 0f
    val previewAlpha by animateFloatAsState(
        targetValue = previewAlphaTarget,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "previewCrossfadeAlpha"
    )

    LaunchedEffect(uiState.selectedMode) {
        // Fade-out quickly, let CameraX rebinding happen, then fade back in.
        previewVisible = false
        delay(120)
        previewVisible = true
    }

    LaunchedEffect(uiState.showSaveSnackbar) {
        if (uiState.showSaveSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = "Videos saved to Movies/DualShot",
                actionLabel = "Open",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed && uiState.savedVideoUris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uiState.savedVideoUris.first(), "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
            viewModel.dismissSnackbar()
        }
    }

    if (showRationale && (!uiState.hasCameraPermission || !uiState.hasAudioPermission)) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Permissions required") },
            text = { Text("Camera and microphone access are required to record dual videos.") },
            confirmButton = {
                TextButton(onClick = {
                    val perms = buildList {
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    permissionLauncher.launch(
                        perms
                    )
                }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer { this.alpha = previewAlpha }
        )

        // Interactive overlays sit above the navigation / gesture bar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .respectNavigationBar()
        ) {
            val showLivePip = uiState.recordingState == RecordingState.IDLE
            if (showLivePip) {
                DraggablePipView(
                    previewBitmap = pipPreviewBitmap,
                    position = uiState.pipPosition,
                    widthDp = uiState.pipSizeDp.widthDp,
                    heightDp = uiState.pipSizeDp.heightDp,
                    recordButtonBounds = recordButtonRect,
                    onDrag = viewModel::updatePipPosition,
                    onDragEnd = {
                        with(density) {
                            viewModel.snapPipToCorner(
                                parentWidth = previewView.width.toFloat().coerceAtLeast(1f),
                                parentHeight = previewView.height.toFloat().coerceAtLeast(1f),
                                pipWidth = uiState.pipSizeDp.widthDp.dp.toPx(),
                                pipHeight = uiState.pipSizeDp.heightDp.dp.toPx()
                            )
                        }
                    }
                )
            } else {
                DraggableLandscapeOverlay(
                    previewBitmap = pipPreviewBitmap,
                    recordingState = uiState.recordingState,
                    position = uiState.pipPosition,
                    widthDp = uiState.pipSizeDp.widthDp,
                    heightDp = uiState.pipSizeDp.heightDp,
                    recordButtonBounds = recordButtonRect,
                    onDrag = viewModel::updatePipPosition,
                    onDragEnd = {
                        with(density) {
                            viewModel.snapPipToCorner(
                                parentWidth = previewView.width.toFloat().coerceAtLeast(1f),
                                parentHeight = previewView.height.toFloat().coerceAtLeast(1f),
                                pipWidth = uiState.pipSizeDp.widthDp.dp.toPx(),
                                pipHeight = uiState.pipSizeDp.heightDp.dp.toPx()
                            )
                        }
                    }
                )
            }

            TopControlsBar(
                isFlashOn = uiState.isFlashOn,
                selectedMode = uiState.selectedMode,
                onFlashToggle = viewModel::toggleFlash,
                onModeClick = { showModeSheet = true },
                onSettingsClick = onNavigateSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomControlsBar(
                    recordingState = uiState.recordingState,
                    elapsedMs = uiState.elapsedTimeMs,
                    storageRemainingSeconds = uiState.storageRemainingSeconds,
                    config = uiState.config,
                    onResolutionSelected = viewModel::setResolution,
                    onFpsSelected = viewModel::setFps
                )
                Spacer(modifier = Modifier.height(24.dp))
                RecordButton(
                    recordingState = uiState.recordingState,
                    isPressed = isPressingRecord,
                    shakeTrigger = uiState.recordButtonShake,
                    onPressStart = {
                        isPressingRecord = true
                        if (uiState.recordingState == RecordingState.IDLE) {
                            viewModel.startRecording()
                        }
                    },
                    onPressEnd = {
                        isPressingRecord = false
                        if (uiState.recordingState == RecordingState.RECORDING) {
                            viewModel.stopRecording()
                        }
                        if (uiState.recordButtonShake) viewModel.clearShake()
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        recordButtonRect = coords.boundsInRoot()
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showModeSheet) {
        ModeBottomSheet(
            selectedMode = uiState.selectedMode,
            isDualLensSupported = uiState.isDualLensSupported,
            showWarning = uiState.showDualLensWarning,
            onSelect = {
                viewModel.setRecordingMode(it)
                showModeSheet = false
            },
            onDismiss = { showModeSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeBottomSheet(
    selectedMode: DualRecordingConfig.RecordingMode,
    isDualLensSupported: Boolean,
    showWarning: Boolean,
    onSelect: (DualRecordingConfig.RecordingMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Dual Lens",
            modifier = Modifier.padding(16.dp),
            color = if (selectedMode == DualRecordingConfig.RecordingMode.DUAL_LENS) {
                Color.White
            } else {
                Color.Gray
            }
        )
        TextButton(onClick = { onSelect(DualRecordingConfig.RecordingMode.DUAL_LENS) }) {
            Text("Use wide + ultrawide (Android 13+)")
        }
        if (!isDualLensSupported || showWarning) {
            Text(
                text = "Dual Lens requires Android 13+ and concurrent camera hardware.",
                color = Color.Yellow,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        TextButton(onClick = { onSelect(DualRecordingConfig.RecordingMode.SINGLE_LENS) }) {
            Text("Single Lens — crop both aspects from one sensor")
        }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}
