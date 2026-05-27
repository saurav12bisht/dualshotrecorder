package com.dualshot.recorder.data.camera

import android.media.MediaMuxer
import android.util.Log

private const val TAG = "SafeMediaMuxer"

/**
 * Wraps [MediaMuxer] with explicit lifecycle to avoid stop/release on unstarted muxers.
 */
class SafeMediaMuxer(
    private val muxer: MediaMuxer
) {
    enum class State { CREATED, STARTED, STOPPED, RELEASED }

    @Volatile var state: State = State.CREATED
        private set

    fun addTrack(format: android.media.MediaFormat): Int {
        check(state == State.CREATED) { "Cannot add track after muxer started" }
        return muxer.addTrack(format)
    }

    fun start() {
        if (state != State.CREATED) return
        muxer.start()
        state = State.STARTED
        Log.d(TAG, "Muxer started")
    }

    fun writeSampleData(trackIndex: Int, data: java.nio.ByteBuffer, info: android.media.MediaCodec.BufferInfo) {
        if (state != State.STARTED || trackIndex < 0) return
        muxer.writeSampleData(trackIndex, data, info)
    }

    fun stopAndRelease() {
        when (state) {
            State.STARTED -> {
                runCatching { muxer.stop() }
                    .onFailure { Log.w(TAG, "muxer.stop failed", it) }
                state = State.STOPPED
            }
            State.CREATED -> {
                // Never started — skip stop() to avoid MPEG4Writer errors.
                Log.w(TAG, "Muxer released without start")
            }
            else -> Unit
        }
        if (state != State.RELEASED) {
            runCatching { muxer.release() }
                .onFailure { Log.w(TAG, "muxer.release failed", it) }
            state = State.RELEASED
        }
    }
}
