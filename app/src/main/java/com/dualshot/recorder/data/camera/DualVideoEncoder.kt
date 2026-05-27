package com.dualshot.recorder.data.camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DualVideoEncoder"
private const val INPUT_TIMEOUT_US = 10_000L
/**
 * Encodes a single H.264 stream and writes the same encoded frames into two muxers.
 */
class DualVideoEncoder(
    width: Int,
    height: Int,
    private val bitrateBps: Int,
    private val fps: Int,
    private val muxerV: MediaMuxer,
    private val muxerH: MediaMuxer,
    private val muxerVLock: Any,
    private val muxerHLock: Any,
    private val onTracksAdded: () -> Unit
) {
    private var codec: MediaCodec? = null
    private val codecLock = Any()
    private val encodedWidth = MediaCodecConfig.even(width)
    private val encodedHeight = MediaCodecConfig.even(height)
    private val expectedFrameSize = encodedWidth * encodedHeight * 3 / 2
    private val stopped = AtomicBoolean(false)

    private var trackV = -1
    private var trackH = -1
    private var muxerVReady = false
    private var muxerHReady = false

    private val pendingV = ArrayBlockingQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>>(120)
    private val pendingH = ArrayBlockingQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>>(120)

    fun start() {
        synchronized(codecLock) {
            val format = MediaCodecConfig.createH264Format(
                encodedWidth,
                encodedHeight,
                bitrateBps,
                fps
            )
            val created = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            created.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            created.start()
            codec = created
            Log.d(TAG, "Started encoder ${encodedWidth}x$encodedHeight @ $fps fps")
        }
    }

    fun encodeFrame(nv12: ByteArray, ptsUs: Long): Boolean {
        if (stopped.get()) return false
        val c = codec ?: return false
        if (nv12.size < expectedFrameSize) {
            Log.w(TAG, "NV12 buffer too small: ${nv12.size} < $expectedFrameSize")
            return false
        }
        synchronized(codecLock) {
            val current = codec ?: return false
            return try {
                val inIdx = current.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inIdx < 0) return false
                current.getInputBuffer(inIdx)?.apply {
                    clear()
                    put(nv12, 0, expectedFrameSize)
                }
                current.queueInputBuffer(inIdx, 0, expectedFrameSize, ptsUs, 0)
                drainAvailable(current)
                true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "encodeFrame skipped — codec not accepting input", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "encodeFrame failed", e)
                false
            }
        }
    }

    fun notifyMuxerStarted(vertical: Boolean) {
        if (vertical) {
            synchronized(muxerVLock) {
                muxerVReady = true
                flushPending(pendingV, trackV, muxerV)
            }
        } else {
            synchronized(muxerHLock) {
                muxerHReady = true
                flushPending(pendingH, trackH, muxerH)
            }
        }
    }

    fun stop(timeoutMs: Long = 5_000L) {
        if (!stopped.compareAndSet(false, true)) return
        synchronized(codecLock) {
            val c = codec
            codec = null
            if (c == null) return

            val deadline = System.currentTimeMillis() + timeoutMs
            try {
                val inIdx = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inIdx >= 0) {
                    c.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainUntilEos(c, deadline)
            } catch (e: Exception) {
                Log.e(TAG, "Encoder stop error", e)
            } finally {
                runCatching { c.stop() }
                runCatching { c.release() }
            }
        }
    }

  /** Drains all currently available encoded output (non-blocking). */
    private fun drainAvailable(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = try {
                c.dequeueOutputBuffer(info, 0L)
            } catch (e: IllegalStateException) {
                return
            }
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> handleFormatChanged(c)
                outIdx >= 0 -> {
                    handleOutputBuffer(c, outIdx, info)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> return // INFO_TRY_AGAIN_LATER or unknown — stop draining
            }
        }
    }

    /** Drains until EOS or [deadlineMs], used when stopping the encoder. */
    private fun drainUntilEos(c: MediaCodec, deadlineMs: Long) {
        val info = MediaCodec.BufferInfo()
        while (System.currentTimeMillis() < deadlineMs) {
            val outIdx = try {
                c.dequeueOutputBuffer(info, INPUT_TIMEOUT_US)
            } catch (e: IllegalStateException) {
                return
            }
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> handleFormatChanged(c)
                outIdx >= 0 -> {
                    handleOutputBuffer(c, outIdx, info)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                else -> return
            }
        }
    }

    private fun handleFormatChanged(c: MediaCodec) {
        if (trackV >= 0) return
        val fmt = c.outputFormat
        synchronized(muxerVLock) {
            trackV = muxerV.addTrack(fmt)
        }
        synchronized(muxerHLock) {
            trackH = muxerH.addTrack(fmt)
        }
        onTracksAdded()
    }

    private fun handleOutputBuffer(c: MediaCodec, outIdx: Int, info: MediaCodec.BufferInfo) {
        val raw = c.getOutputBuffer(outIdx) ?: return
        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
        if (!isConfig && info.size > 0 && trackV >= 0 && trackH >= 0) {
            val sampleV = copySample(raw, info)
            val sampleH = copySample(raw, info)
            writeOrQueueVertical(sampleV.first, sampleV.second)
            writeOrQueueHorizontal(sampleH.first, sampleH.second)
        }
        runCatching { c.releaseOutputBuffer(outIdx, false) }
    }

    private fun writeOrQueueVertical(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(muxerVLock) {
            if (muxerVReady && trackV >= 0) {
                runCatching { muxerV.writeSampleData(trackV, buf, info) }
                    .onFailure { Log.e(TAG, "writeSampleData vertical failed", it) }
            } else {
                pendingV.offer(buf to info)
            }
        }
    }

    private fun writeOrQueueHorizontal(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(muxerHLock) {
            if (muxerHReady && trackH >= 0) {
                runCatching { muxerH.writeSampleData(trackH, buf, info) }
                    .onFailure { Log.e(TAG, "writeSampleData horizontal failed", it) }
            } else {
                pendingH.offer(buf to info)
            }
        }
    }

    private fun flushPending(
        pending: ArrayBlockingQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>>,
        trackIndex: Int,
        muxer: MediaMuxer
    ) {
        if (trackIndex < 0) return
        val list = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>(pending.size)
        while (true) {
            val s = pending.poll() ?: break
            list.add(s)
        }
        list.sortBy { it.second.presentationTimeUs }
        for ((buf, info) in list) {
            runCatching { muxer.writeSampleData(trackIndex, buf, info) }
                .onFailure { Log.e(TAG, "flushPending write failed", it) }
        }
    }

    private fun copySample(
        src: ByteBuffer,
        info: MediaCodec.BufferInfo
    ): Pair<ByteBuffer, MediaCodec.BufferInfo> {
        val copy = ByteBuffer.allocate(info.size)
        src.position(info.offset).limit(info.offset + info.size)
        copy.put(src)
        copy.flip()
        val infoCopy = MediaCodec.BufferInfo().apply {
            set(0, info.size, info.presentationTimeUs, info.flags)
        }
        return copy to infoCopy
    }
}
