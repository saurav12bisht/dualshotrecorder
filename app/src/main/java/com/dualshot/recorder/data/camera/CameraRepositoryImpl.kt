package com.dualshot.recorder.data.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dualshot.recorder.domain.model.DualRecordingConfig
import com.dualshot.recorder.domain.model.RecordingState
import com.dualshot.recorder.domain.model.VideoFile
import com.dualshot.recorder.domain.repository.CameraRepository
import com.dualshot.recorder.domain.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "CameraRepositoryImpl"

/**
 * CameraX repository: one Preview (GPU), YUV analysis copied quickly then processed
 * on background encode / PiP threads so the UI and camera stay smooth.
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val recorder: DualStreamRecorder
) : CameraRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analysisExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dualshot-analysis")
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var activeConfig: DualRecordingConfig? = null
    private var timerJob: kotlinx.coroutines.Job? = null
    private var lastAnalysisRotation = 90
    private var lastAnalysisWidth = 1280
    private var lastAnalysisHeight = 720
    private val pipRenderer = PipPreviewRenderer()

    private val _pipPreviewFlow = MutableStateFlow<Bitmap?>(null)
    private val framePipeline = FramePipeline(
        recorder = recorder,
        pipRenderer = pipRenderer,
        onPipBitmap = { bitmap -> _pipPreviewFlow.value = bitmap }
    )

    /** Throttled PiP preview bitmap for UI (not used for encoding). */
    val pipPreviewFlow: StateFlow<Bitmap?> = _pipPreviewFlow.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    override val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedTimeMs = MutableStateFlow(0L)
    override val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    override val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    override val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    override val isDualLensSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)

    override suspend fun bindCamera(
        previewView: PreviewView,
        config: DualRecordingConfig
    ) {
        activeConfig = config
        val provider = obtainProvider()
        cameraProvider = provider
        val owner = previewView.context as? LifecycleOwner
            ?: error("PreviewView must be attached to a LifecycleOwner")
        lifecycleOwner = owner
        provider.unbindAll()

        val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(displayRotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    AspectRatio.RATIO_16_9,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(displayRotation)
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    lastAnalysisRotation = imageProxy.imageInfo.rotationDegrees
                    lastAnalysisWidth = imageProxy.width
                    lastAnalysisHeight = imageProxy.height
                    val recording = _recordingState.value == RecordingState.RECORDING

                    if (!recording) {
                        val nowNs = imageProxy.imageInfo.timestamp
                        if (nowNs - framePipeline.lastPipUpdateNs < framePipeline.pipIdleIntervalNs) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                    }

                    val snapshot = YuvFrameSnapshot.capture(imageProxy)
                    imageProxy.close()
                    if (snapshot == null) return@setAnalyzer

                    framePipeline.submitFrame(snapshot, recording)
                }
            }

        try {
            camera = provider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Camera bind failed — retrying preview-only", e)
            camera = provider.bindToLifecycle(
                owner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
            )
        }
    }

    override suspend fun startRecording(config: DualRecordingConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (_recordingState.value == RecordingState.RECORDING) {
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }
            val effectiveConfig = config.copy(
                resolution = DualRecordingConfig.Resolution.P1080
            )
            val remaining = videoRepository.estimateRemainingSeconds(
                effectiveConfig.bitRate.resolveBitRate(effectiveConfig.resolution)
            )
            if (remaining < 5) {
                _recordingState.value = RecordingState.ERROR
                return@withContext Result.failure(StorageFullException())
            }
            activeConfig = effectiveConfig
            try {
                recorder.start(
                    effectiveConfig,
                    context.cacheDir,
                    lastAnalysisRotation,
                    lastAnalysisWidth.coerceIn(1, 3840),
                    lastAnalysisHeight.coerceIn(1, 3840)
                )
                _recordingState.value = RecordingState.RECORDING
                _elapsedTimeMs.value = 0L
                startTimer()
                Result.success(Unit)
            } catch (t: Throwable) {
                _recordingState.value = RecordingState.ERROR
                recorder.release()
                Result.failure(t)
            }
        }

    override suspend fun stopRecording(): Result<List<VideoFile>> = withContext(Dispatchers.IO) {
        if (_recordingState.value != RecordingState.RECORDING) {
            return@withContext Result.failure(IllegalStateException("Not recording"))
        }
        _recordingState.value = RecordingState.SAVING
        timerJob?.cancel()
        val config = activeConfig ?: DualRecordingConfig()
        try {
            val (vertical, horizontal) = recorder.stop()
            val result = videoRepository.saveToMediaStore(
                verticalTemp = vertical,
                horizontalTemp = horizontal,
                extension = config.format.extension
            )
            _recordingState.value = if (result.isSuccess) RecordingState.IDLE else RecordingState.ERROR
            result
        } catch (t: Throwable) {
            _recordingState.value = RecordingState.ERROR
            recorder.release()
            Result.failure(t)
        }
    }

    override fun setFlashEnabled(enabled: Boolean) {
        _isFlashOn.value = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    override fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(1f, camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 10f)
        _zoomRatio.value = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    override fun release() {
        timerJob?.cancel()
        framePipeline.shutdown()
        recorder.release()
        cameraProvider?.unbindAll()
        camera = null
        pipRenderer.release()
        _pipPreviewFlow.value = null
    }

    override fun observeThermalState(): Flow<Boolean> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(false)
            delay(5_000)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_recordingState.value == RecordingState.RECORDING) {
                delay(100)
                _elapsedTimeMs.value += 100
            }
        }
    }

    private suspend fun obtainProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }

    /** Thrown when device storage cannot sustain further recording. */
    class StorageFullException : Exception("Storage full")
}
