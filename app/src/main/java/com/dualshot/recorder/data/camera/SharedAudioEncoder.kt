package com.dualshot.recorder.data.camera

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SharedAudioEncoder"
private const val SAMPLE_RATE = 44_100
private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
private const val PCM_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val BITRATE = 128_000
private const val TIMEOUT_US = 10_000L
private const val BYTES_PER_SAMPLE = 2 // 16-bit mono

/**
 * Single microphone capture feeding AAC into two muxers (vertical + horizontal files).
 */
class SharedAudioEncoder(
    private val muxerV: MediaMuxer,
    private val muxerH: MediaMuxer,
    private val muxerVLock: Any,
    private val muxerHLock: Any,
    private val onTracksReady: () -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var trackV = -1
    private var trackH = -1
    private var muxerVReady = false
    private var muxerHReady = false
    private val pendingV = ArrayBlockingQueue<EncodedSample>(64)
    private val pendingH = ArrayBlockingQueue<EncodedSample>(64)
    private var inputPtsUs = 0L
    private val stopped = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    var tracksAdded: Boolean = false
        private set

    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, PCM_FORMAT)
        if (minBuf <= 0) {
            throw IllegalStateException("Invalid AudioRecord buffer size: $minBuf")
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            SAMPLE_RATE,
            CHANNEL_IN,
            PCM_FORMAT,
            minBuf * 4
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        audioRecord = record

        val format = MediaCodecConfig.createAacFormat()
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        encoder = enc

        record.startRecording()
        val pcm = ByteArray(minBuf)
        captureJob = scope.launch {
            while (isActive) {
                try {
                    val read = audioRecord?.read(pcm, 0, pcm.size) ?: break
                    if (read > 0) feedPcm(pcm, read)
                } catch (e: Exception) {
                    Log.e(TAG, "Audio capture failed", e)
                    break
                }
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

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        captureJob?.cancel()
        scope.cancel()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        val enc = encoder
        encoder = null
        if (enc == null) return
        runCatching {
            val inIdx = enc.dequeueInputBuffer(TIMEOUT_US)
            if (inIdx >= 0) {
                enc.queueInputBuffer(inIdx, 0, 0, inputPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainOutput(enc, eos = true)
        }
        runCatching { enc.stop() }
        runCatching { enc.release() }
    }

    private fun feedPcm(data: ByteArray, length: Int) {
        if (stopped.get()) return
        val enc = encoder ?: return
        try {
            val pts = inputPtsUs
            inputPtsUs += length * 1_000_000L / (SAMPLE_RATE * BYTES_PER_SAMPLE)

            val inIdx = enc.dequeueInputBuffer(0)
            if (inIdx >= 0) {
                enc.getInputBuffer(inIdx)?.apply {
                    clear()
                    put(data, 0, length)
                }
                enc.queueInputBuffer(inIdx, 0, length, pts, 0)
            }
            drainOutput(enc, eos = false)
        } catch (e: Exception) {
            Log.e(TAG, "feedPcm failed", e)
        }
    }

    private fun drainOutput(enc: MediaCodec, eos: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = try {
                enc.dequeueOutputBuffer(info, if (eos) TIMEOUT_US else 0)
            } catch (e: IllegalStateException) {
                return
            }
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackV < 0) {
                        val fmt = enc.outputFormat
                        synchronized(muxerVLock) { trackV = muxerV.addTrack(fmt) }
                        synchronized(muxerHLock) { trackH = muxerH.addTrack(fmt) }
                        tracksAdded = true
                        onTracksReady()
                    }
                }
                outIdx >= 0 -> {
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && trackV >= 0 && trackH >= 0) {
                        val raw = enc.getOutputBuffer(outIdx) ?: continue
                        writeToMuxers(raw, info)
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> return
            }
        }
    }

    private fun writeToMuxers(raw: ByteBuffer, info: MediaCodec.BufferInfo) {
        val forV = EncodedSample(copyBuffer(raw, info), copyInfo(info))
        val forH = EncodedSample(copyBuffer(raw, info), copyInfo(info))
        synchronized(muxerVLock) { writeOrQueue(forV, muxerVReady, pendingV, trackV, muxerV) }
        synchronized(muxerHLock) { writeOrQueue(forH, muxerHReady, pendingH, trackH, muxerH) }
    }

    private fun writeOrQueue(
        sample: EncodedSample,
        muxerReady: Boolean,
        pending: ArrayBlockingQueue<EncodedSample>,
        trackIndex: Int,
        muxer: MediaMuxer
    ) {
        if (!muxerReady || trackIndex < 0) {
            pending.offer(sample.copy())
            return
        }
        runCatching { muxer.writeSampleData(trackIndex, sample.data, sample.info) }
            .onFailure { Log.e(TAG, "writeSampleData failed", it) }
    }

    private fun flushPending(
        pending: ArrayBlockingQueue<EncodedSample>,
        trackIndex: Int,
        muxer: MediaMuxer
    ) {
        if (trackIndex < 0) return
        val sorted = ArrayList<EncodedSample>(pending.size)
        while (true) {
            val s = pending.poll() ?: break
            sorted.add(s)
        }
        sorted.sortBy { it.info.presentationTimeUs }
        for (sample in sorted) {
            runCatching { muxer.writeSampleData(trackIndex, sample.data, sample.info) }
                .onFailure { Log.e(TAG, "flushPending write failed", it) }
        }
    }

    private fun copyBuffer(src: ByteBuffer, info: MediaCodec.BufferInfo): ByteBuffer {
        val copy = ByteBuffer.allocate(info.size)
        src.position(info.offset).limit(info.offset + info.size)
        copy.put(src)
        copy.flip()
        return copy
    }

    private fun copyInfo(info: MediaCodec.BufferInfo): MediaCodec.BufferInfo =
        MediaCodec.BufferInfo().apply {
            set(0, info.size, info.presentationTimeUs, info.flags)
        }

    private data class EncodedSample(
        val data: ByteBuffer,
        val info: MediaCodec.BufferInfo
    ) {
        fun copy(): EncodedSample {
            val dup = ByteBuffer.allocate(data.remaining())
            dup.put(data.duplicate())
            dup.flip()
            return EncodedSample(dup, MediaCodec.BufferInfo().apply {
                set(0, dup.remaining(), info.presentationTimeUs, info.flags)
            })
        }
    }
}
