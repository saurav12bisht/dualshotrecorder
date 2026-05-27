package com.dualshot.recorder.data.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue

private const val TAG = "VideoEncoder"
private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
private const val INPUT_TIMEOUT_US = 5_000L
private const val OUTPUT_TIMEOUT_US = 0L

/**
 * H.264 encoder using ByteBuffer (YUV420 flexible) input — no Surface/Canvas.
 */
class VideoEncoder(
    val width: Int,
    val height: Int,
    private val bitrateBps: Int,
    private val fps: Int,
    private val muxer: MediaMuxer,
    private val onTrackAdded: (Int) -> Unit
) {
    private var codec: MediaCodec? = null
    var trackIndex: Int = -1
        private set
    private var muxerReady = false
    private val pendingSamples = ArrayBlockingQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>>(120)
    private val muxerLock = Any()

    /**
     * Configures and starts the encoder.
     */
    fun start() {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(
                MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            )
            setInteger(
                MediaFormat.KEY_LEVEL,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31
            )
        }
        codec = MediaCodec.createEncoderByType(MIME).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        Log.d(TAG, "Started encoder ${width}x$height @ $fps fps")
    }

    /**
     * Queues one NV12 frame. Returns false if the encoder input queue is full (frame dropped).
     */
    fun encodeFrame(nv12: ByteArray, ptsUs: Long): Boolean {
        val c = codec ?: return false
        val inIdx = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inIdx < 0) return false
        c.getInputBuffer(inIdx)?.apply {
            clear()
            put(nv12)
        }
        c.queueInputBuffer(inIdx, 0, nv12.size, ptsUs, 0)
        drainAllAvailable()
        return true
    }

    fun notifyMuxerStarted() {
        synchronized(muxerLock) {
            val sorted = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>(pendingSamples.size)
            while (true) {
                val sample = pendingSamples.poll() ?: break
                sorted.add(sample)
            }
            sorted.sortBy { it.second.presentationTimeUs }
            muxerReady = true
            if (trackIndex >= 0) {
                for ((buf, info) in sorted) {
                    muxer.writeSampleData(trackIndex, buf, info)
                }
            }
        }
    }

    fun stop(timeoutMs: Long = 5_000L) {
        val c = codec ?: return
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            val inIdx = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inIdx >= 0) {
                c.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(eos = true, deadlineMs = deadline)
        } catch (e: Exception) {
            Log.e(TAG, "Encoder stop error", e)
        } finally {
            runCatching { c.stop() }
            runCatching { c.release() }
            codec = null
        }
    }

    /** Drains all currently available output buffers (keeps input queue from backing up). */
    private fun drainAllAvailable() {
        drain(eos = false, deadlineMs = Long.MAX_VALUE, untilIdle = true)
    }

    private fun drain(eos: Boolean, deadlineMs: Long, untilIdle: Boolean = false) {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        while (System.currentTimeMillis() < deadlineMs) {
            val outIdx = try {
                c.dequeueOutputBuffer(info, if (eos) INPUT_TIMEOUT_US else OUTPUT_TIMEOUT_US)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "dequeueOutputBuffer in bad state", e)
                return
            }
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackIndex < 0) {
                        trackIndex = muxer.addTrack(c.outputFormat)
                        onTrackAdded(trackIndex)
                    }
                }
                outIdx >= 0 -> {
                    val raw = c.getOutputBuffer(outIdx) ?: continue
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (!isConfig && info.size > 0 && trackIndex >= 0) {
                        val copy = ByteBuffer.allocate(info.size)
                        raw.position(info.offset).limit(info.offset + info.size)
                        copy.put(raw)
                        copy.flip()
                        val infoCopy = MediaCodec.BufferInfo().apply {
                            set(0, info.size, info.presentationTimeUs, info.flags)
                        }
                        synchronized(muxerLock) {
                            if (muxerReady && trackIndex >= 0) {
                                muxer.writeSampleData(trackIndex, copy, infoCopy)
                            } else {
                                pendingSamples.offer(copy to infoCopy)
                            }
                        }
                    }
                    runCatching { c.releaseOutputBuffer(outIdx, false) }
                    if (isEos) return
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilIdle || eos) return
                }
                else -> return
            }
        }
    }
}
