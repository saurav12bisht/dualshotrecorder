package com.dualshot.recorder.domain.model

/**
 * Represents the lifecycle state of a dual recording session.
 */
enum class RecordingState {
    /** Camera is idle and not recording. */
    IDLE,

    /** Actively capturing and encoding both streams. */
    RECORDING,

    /** Finalizing encoders and writing files to MediaStore. */
    SAVING,

    /** An unrecoverable error occurred during recording or saving. */
    ERROR
}
