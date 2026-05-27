package com.dualshot.recorder.data.camera

import android.media.MediaMuxer
import android.util.Log
import com.dualshot.recorder.domain.model.DualRecordingConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DualStreamRecorder"

/**
 * Orchestrates dual MP4 output (vertical + horizontal) from one camera analysis stream.
 */
@Singleton
class DualStreamRecorder @Inject constructor() {

    private lateinit var verticalFile: File
    private lateinit var horizontalFile: File

    private var muxerVLifecycle: MuxerLifecycle? = null
    private var muxerHLifecycle: MuxerLifecycle? = null
    private val muxerVLock = Any()
    private val muxerHLock = Any()

    private var videoEncoder: DualVideoEncoder? = null
    private var audioEncoder: SharedAudioEncoder? = null
    private var conv: YuvToNV12Converter? = null

    @Volatile private var vVideoReady = false
    @Volatile private var vAudioReady = false
    @Volatile private var hVideoReady = false
    @Volatile private var hAudioReady = false

    /** Blocks [onFrame] immediately when stop begins. */
    private val acceptFrames = AtomicBoolean(false)

    @Volatile var isRecording = false
        private set

    private var targetFps = 30
    private var frameDurationUs = 33_333L
    private var nextFramePtsUs = 0L
    private var encodedFrameCount = 0
    private var sensorRotationDegrees = 90
    private var encodeWidth = RECORD_ENCODE_WIDTH
    private var encodeHeight = RECORD_ENCODE_HEIGHT

    @Synchronized
    fun start(
        config: DualRecordingConfig,
        outputDir: File,
        rotationDegrees: Int,
        analysisWidth: Int,
        analysisHeight: Int
    ) {
        if (isRecording) return

        releaseMuxerState()

        outputDir.mkdirs()
        val ts = System.currentTimeMillis()
        verticalFile = File(outputDir, "vertical_$ts.mp4")
        horizontalFile = File(outputDir, "horizontal_$ts.mp4")

        val (encW, encH) = MediaCodecConfig.resolveEncodeSize(analysisWidth, analysisHeight)
        encodeWidth = encW
        encodeHeight = encH

        val bitrate = (config.bitRate.resolveBitRate(config.resolution) * 0.75f).toInt()
            .coerceIn(2_000_000, 8_000_000)
        targetFps = config.fps.coerceIn(24, 30)
        frameDurationUs = 1_000_000L / targetFps
        nextFramePtsUs = 0L
        encodedFrameCount = 0
        sensorRotationDegrees = ((rotationDegrees % 360) + 360) % 360

        vVideoReady = false
        vAudioReady = false
        hVideoReady = false
        hAudioReady = false

        val muxerV = MediaMuxer(verticalFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            setOrientationHint(sensorRotationDegrees)
        }
        val muxerH = MediaMuxer(horizontalFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            setOrientationHint(0)
        }
        muxerVLifecycle = MuxerLifecycle(muxerV)
        muxerHLifecycle = MuxerLifecycle(muxerH)

        conv = YuvToNV12Converter(
            dstWidth = encodeWidth,
            dstHeight = encodeHeight,
            targetAspect = YuvToNV12Converter.TargetAspect.HORIZONTAL,
            rotationDegrees = sensorRotationDegrees
        )

        try {
            videoEncoder = DualVideoEncoder(
                width = encodeWidth,
                height = encodeHeight,
                bitrateBps = bitrate,
                fps = targetFps,
                muxerV = muxerV,
                muxerH = muxerH,
                muxerVLock = muxerVLock,
                muxerHLock = muxerHLock
            ) {
                vVideoReady = true
                hVideoReady = true
                tryStartMuxers()
            }.also { it.start() }

            audioEncoder = SharedAudioEncoder(
                muxerV = muxerV,
                muxerH = muxerH,
                muxerVLock = muxerVLock,
                muxerHLock = muxerHLock
            ) {
                vAudioReady = true
                hAudioReady = true
                tryStartMuxers()
            }.also { it.start() }

            acceptFrames.set(true)
            isRecording = true
            Log.d(
                TAG,
                "Recording started ${encodeWidth}x$encodeHeight @ $targetFps fps, rotation=$sensorRotationDegrees"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording pipeline", e)
            acceptFrames.set(false)
            isRecording = false
            releaseMuxerState()
            throw e
        }
    }

    fun onFrame(snapshot: YuvFrameSnapshot) {
        if (!acceptFrames.get()) return
        try {
            conv?.setRotation(snapshot.rotationDegrees)
            val ptsUs = nextFramePtsUs
            val nv12 = conv?.convert(snapshot.planeBuffers()) ?: return
            if (videoEncoder?.encodeFrame(nv12, ptsUs) == true) {
                nextFramePtsUs += frameDurationUs
                encodedFrameCount++
                if (encodedFrameCount == 1 || encodedFrameCount % 30 == 0) {
                    Log.d(TAG, "Encoded $encodedFrameCount video frames")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame encode failed", e)
        }
    }

    @Synchronized
    fun stop(): Pair<File, File> {
        acceptFrames.set(false)
        isRecording = false

        runCatching { audioEncoder?.stop() }
        runCatching { videoEncoder?.stop() }

        muxerVLifecycle?.stop()
        muxerHLifecycle?.stop()

        releaseMuxerState()
        Log.d(TAG, "Recording stopped")
        return Pair(verticalFile, horizontalFile)
    }

    @Synchronized
    fun release() {
        acceptFrames.set(false)
        isRecording = false
        runCatching { audioEncoder?.stop() }
        runCatching { videoEncoder?.stop() }
        releaseMuxerState()
    }

    @Synchronized
    private fun tryStartMuxers() {
        if (muxerVLifecycle?.state == MuxerLifecycle.State.CREATED &&
            vVideoReady && vAudioReady
        ) {
            runCatching {
                muxerVLifecycle?.start()
                videoEncoder?.notifyMuxerStarted(vertical = true)
                audioEncoder?.notifyMuxerStarted(vertical = true)
                Log.d(TAG, "Vertical muxer started")
            }.onFailure { e ->
                Log.e(TAG, "Vertical muxer start failed", e)
                acceptFrames.set(false)
                isRecording = false
            }
        }
        if (muxerHLifecycle?.state == MuxerLifecycle.State.CREATED &&
            hVideoReady && hAudioReady
        ) {
            runCatching {
                muxerHLifecycle?.start()
                videoEncoder?.notifyMuxerStarted(vertical = false)
                audioEncoder?.notifyMuxerStarted(vertical = false)
                Log.d(TAG, "Horizontal muxer started")
            }.onFailure { e ->
                Log.e(TAG, "Horizontal muxer start failed", e)
                acceptFrames.set(false)
                isRecording = false
            }
        }
    }

    private fun releaseMuxerState() {
        muxerVLifecycle?.release()
        muxerHLifecycle?.release()
        muxerVLifecycle = null
        muxerHLifecycle = null
        videoEncoder = null
        audioEncoder = null
        conv = null
    }
}
