package com.dualshot.recorder.data.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log

private const val TAG = "MediaCodecConfig"

/** Stable 1080p landscape encode size (matches ImageAnalysis target). */
const val RECORD_ENCODE_WIDTH = 1280
const val RECORD_ENCODE_HEIGHT = 720

/**
 * Helpers for creating encoders with device-supported formats.
 */
object MediaCodecConfig {

    fun even(value: Int): Int = value.coerceAtLeast(2) and 0xFFFFFFFE.toInt()

    private val h264ColorFormat: Int by lazy(LazyThreadSafetyMode.NONE) {
        pickH264InputColorFormat()
    }

    /**
     * Normalizes analysis dimensions to a safe H.264 encode size (max 1280×720 landscape).
     */
    fun resolveEncodeSize(analysisWidth: Int, analysisHeight: Int): Pair<Int, Int> {
        var w = analysisWidth.coerceAtLeast(2)
        var h = analysisHeight.coerceAtLeast(2)
        if (w > 1920 || h > 1920 || w * h > RECORD_ENCODE_WIDTH * RECORD_ENCODE_HEIGHT * 2) {
            Log.w(TAG, "Analysis ${w}x$h too large — using ${RECORD_ENCODE_WIDTH}x$RECORD_ENCODE_HEIGHT")
            w = RECORD_ENCODE_WIDTH
            h = RECORD_ENCODE_HEIGHT
        }
        w = even(w)
        h = even(h)
        return maxOf(w, h) to minOf(w, h)
    }

    private fun pickH264InputColorFormat(): Int {
        val codec = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC) }
            .getOrNull() ?: return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        return try {
            val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val supported = caps.colorFormats.toSet()
            when {
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                else -> caps.colorFormats.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query H.264 color formats, using flexible", e)
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        } finally {
            runCatching { codec.release() }
        }
    }

    fun createH264Format(width: Int, height: Int, bitrateBps: Int, fps: Int): MediaFormat {
        val w = even(width)
        val h = even(height)
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, h264ColorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps.coerceIn(2_000_000, 12_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
    }

    fun createAacFormat(): MediaFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44_100, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
}
