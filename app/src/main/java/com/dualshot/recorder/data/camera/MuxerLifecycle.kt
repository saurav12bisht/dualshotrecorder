package com.dualshot.recorder.data.camera

import android.media.MediaMuxer
import android.util.Log

private const val TAG = "MuxerLifecycle"

/**
 * Tracks [MediaMuxer] state so we never call [MediaMuxer.stop] twice or stop before [MediaMuxer.start].
 */
internal class MuxerLifecycle(private val muxer: MediaMuxer) {
    enum class State { CREATED, STARTED, STOPPED, RELEASED }

    @Volatile var state: State = State.CREATED
        private set

    fun start() {
        if (state != State.CREATED) return
        muxer.start()
        state = State.STARTED
    }

    fun stop() {
        if (state != State.STARTED) return
        runCatching { muxer.stop() }
            .onFailure { Log.e(TAG, "muxer.stop failed", it) }
        state = State.STOPPED
    }

    fun release() {
        if (state == State.RELEASED) return
        if (state == State.STARTED) {
            runCatching { muxer.stop() }
                .onFailure { Log.e(TAG, "muxer.stop on release failed", it) }
        }
        runCatching { muxer.release() }
        state = State.RELEASED
    }
}
