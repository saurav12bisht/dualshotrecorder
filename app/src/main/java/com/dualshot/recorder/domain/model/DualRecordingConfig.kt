package com.dualshot.recorder.domain.model

/**
 * Configuration for dual-stream recording quality and behavior.
 *
 * @property resolution Target resolution preset.
 * @property fps Target frame rate.
 * @property format Container format (MP4 or MOV alias).
 * @property bitRate Bit rate preset for encoders.
 * @property mode Single or dual physical camera mode.
 */
data class DualRecordingConfig(
    val resolution: Resolution = Resolution.P1080,
    val fps: Int = 30,
    val format: VideoFormat = VideoFormat.MP4,
    val bitRate: BitRatePreset = BitRatePreset.AUTO,
    val mode: RecordingMode = RecordingMode.SINGLE_LENS
) {
    /** Target resolution preset for encoders. */
    enum class Resolution(val verticalWidth: Int, val verticalHeight: Int) {
        P1080(1080, 1920),
        P4K(2160, 3840);

        val horizontalWidth: Int get() = verticalHeight
        val horizontalHeight: Int get() = verticalWidth
    }

    /** Output container format. */
    enum class VideoFormat(val mimeSubType: String, val extension: String) {
        MP4("video/mp4", "mp4"),
        MOV("video/mp4", "mov")
    }

    /** Encoder bit rate preset. */
    enum class BitRatePreset {
        AUTO,
        HIGH,
        MAX;

        /**
         * Resolves bit rate in bits per second for the given resolution.
         */
        fun resolveBitRate(resolution: Resolution): Int = when (this) {
            AUTO -> when (resolution) {
                Resolution.P1080 -> 8_000_000
                Resolution.P4K -> 20_000_000
            }
            HIGH -> when (resolution) {
                Resolution.P1080 -> 12_000_000
                Resolution.P4K -> 35_000_000
            }
            MAX -> when (resolution) {
                Resolution.P1080 -> 16_000_000
                Resolution.P4K -> 50_000_000
            }
        }
    }

    /** Camera binding mode. */
    enum class RecordingMode {
        /** One camera; crop/scale to produce both aspect ratios. */
        SINGLE_LENS,

        /** Wide + ultrawide via ConcurrentCamera (Android 13+). */
        DUAL_LENS
    }
}
