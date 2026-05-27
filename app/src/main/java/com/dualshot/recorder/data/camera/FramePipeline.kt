package com.dualshot.recorder.data.camera

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "FramePipeline"

/**
 * Routes camera frames: latest-frame encode queue + throttled PiP on background threads.
 */
internal class FramePipeline(
    private val recorder: DualStreamRecorder,
    private val pipRenderer: PipPreviewRenderer,
    private val onPipBitmap: (Bitmap) -> Unit
) {
    private val encodeExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dualshot-encode").apply { priority = Thread.NORM_PRIORITY + 1 }
    }
    private val pipExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dualshot-pip")
    }

    private val latestEncodeFrame = AtomicReference<YuvFrameSnapshot?>(null)
    private val encodeLoopRunning = AtomicBoolean(false)
    private val pipBusy = AtomicBoolean(false)
    private val encodeFrameCounter = AtomicInteger(0)

    @Volatile var lastPipUpdateNs = 0L
    var pipIdleIntervalNs = 66_000_000L // ~15 fps
    var pipRecordIntervalNs = 125_000_000L // ~8 fps
    var pipRecordEveryNthEncode = 4

    fun submitFrame(snapshot: YuvFrameSnapshot, recording: Boolean) {
        if (recording) {
            latestEncodeFrame.set(snapshot)
            scheduleEncodeLoop()
        } else {
            submitIdlePip(snapshot)
        }
    }

    fun shutdown() {
        latestEncodeFrame.set(null)
        encodeExecutor.shutdownNow()
        pipExecutor.shutdownNow()
    }

    private fun scheduleEncodeLoop() {
        if (!encodeLoopRunning.compareAndSet(false, true)) return
        encodeExecutor.execute {
            try {
                processEncodeLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Encode loop failed", e)
            } finally {
                encodeLoopRunning.set(false)
                if (latestEncodeFrame.get() != null) {
                    scheduleEncodeLoop()
                }
            }
        }
    }

    private fun processEncodeLoop() {
        while (true) {
            val snapshot = latestEncodeFrame.getAndSet(null) ?: break
            try {
                recorder.onFrame(snapshot)
                val encoded = encodeFrameCounter.incrementAndGet()
                if (encoded % pipRecordEveryNthEncode == 0) {
                    val now = snapshot.timestampNs
                    if (now - lastPipUpdateNs >= pipRecordIntervalNs) {
                        lastPipUpdateNs = now
                        submitPipRender(snapshot)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame encode failed", e)
            }
        }
    }

    private fun submitIdlePip(snapshot: YuvFrameSnapshot) {
        val now = snapshot.timestampNs
        if (now - lastPipUpdateNs < pipIdleIntervalNs) return
        lastPipUpdateNs = now
        submitPipRender(snapshot)
    }

    private fun submitPipRender(snapshot: YuvFrameSnapshot) {
        if (!pipBusy.compareAndSet(false, true)) return
        pipExecutor.execute {
            try {
                val bitmap = pipRenderer.render(
                    snapshot.planeBuffers(),
                    snapshot.rotationDegrees
                )
                onPipBitmap(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "PiP render failed", e)
            } finally {
                pipBusy.set(false)
            }
        }
    }
}
