package com.dualshot.recorder.data.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
/**
 * Renders a small 16:9 PiP preview from YUV frames (Image or copied plane buffers).
 */
class PipPreviewRenderer(
    private val outWidth: Int = 320,
    private val outHeight: Int = 180
) {
    private val rotateMatrix = Matrix()
    private var workBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null
    private var workPixels: IntArray? = null

    fun render(planes: YuvFrameSnapshot.PlaneBuffers, rotationDegrees: Int): Bitmap {
        val needsRotate = rotationDegrees == 90 || rotationDegrees == 270
        val renderW = if (needsRotate) outHeight else outWidth
        val renderH = if (needsRotate) outWidth else outHeight

        val crop = computeCrop(planes.width, planes.height, needsRotate)
        val pixels = workPixels?.takeIf { it.size == renderW * renderH }
            ?: IntArray(renderW * renderH).also { workPixels = it }

        fillPixels(planes, crop, renderW, renderH, pixels)

        val work = obtainWorkBitmap(renderW, renderH)
        work.setPixels(pixels, 0, renderW, 0, 0, renderW, renderH)

        if (!needsRotate) return work

        rotateMatrix.reset()
        when (rotationDegrees) {
            90 -> {
                rotateMatrix.postRotate(90f)
                rotateMatrix.postTranslate(renderH.toFloat(), 0f)
            }
            270 -> {
                rotateMatrix.postRotate(270f)
                rotateMatrix.postTranslate(0f, renderW.toFloat())
            }
            else -> rotateMatrix.postRotate(rotationDegrees.toFloat())
        }
        val out = obtainOutputBitmap()
        android.graphics.Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(work, rotateMatrix, null)
        }
        return out
    }

    fun release() {
        workBitmap?.recycle()
        outputBitmap?.recycle()
        workBitmap = null
        outputBitmap = null
        workPixels = null
    }

    private data class Crop(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun computeCrop(srcW: Int, srcH: Int, cropVerticalInBuffer: Boolean): Crop {
        return if (cropVerticalInBuffer) {
            val cropH = srcH
            val cropW = (srcH * 9f / 16f).toInt().coerceAtMost(srcW)
            Crop((srcW - cropW) / 2, 0, cropW, cropH)
        } else {
            val cropH = srcH
            val cropW = (srcH * 16f / 9f).toInt().coerceAtMost(srcW)
            Crop((srcW - cropW) / 2, 0, cropW, cropH)
        }
    }

    private fun fillPixels(
        planes: YuvFrameSnapshot.PlaneBuffers,
        crop: Crop,
        renderW: Int,
        renderH: Int,
        pixels: IntArray
    ) {
        val yBuf = planes.y
        val uBuf = planes.u
        val vBuf = planes.v
        val yStride = planes.yRowStride
        val uvStride = planes.uvRowStride
        val uvPixel = planes.uvPixelStride
        val yLimit = yBuf.size
        val uvLimit = minOf(uBuf.size, vBuf.size)

        var i = 0
        for (row in 0 until renderH) {
            val srcRow = crop.y + row * crop.h / renderH
            val uvRow = srcRow / 2
            for (col in 0 until renderW) {
                val srcCol = crop.x + col * crop.w / renderW
                val yIdx = (srcRow * yStride + srcCol).coerceIn(0, yLimit - 1)
                val y = yBuf[yIdx].toInt() and 0xFF
                val uvCol = srcCol / 2
                val uvIdx = (uvRow * uvStride + uvCol * uvPixel).coerceIn(0, uvLimit - 1)
                val u = (uBuf[uvIdx].toInt() and 0xFF) - 128
                val v = (vBuf[uvIdx].toInt() and 0xFF) - 128
                pixels[i++] = yuvToRgb(y, u, v)
            }
        }
    }

    private fun obtainWorkBitmap(w: Int, h: Int): Bitmap {
        val existing = workBitmap
        if (existing != null && !existing.isRecycled && existing.width == w && existing.height == h) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { workBitmap = it }
    }

    private fun obtainOutputBitmap(): Bitmap {
        val existing = outputBitmap
        if (existing != null && !existing.isRecycled &&
            existing.width == outWidth && existing.height == outHeight
        ) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.RGB_565).also { outputBitmap = it }
    }

    private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
        val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
        val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
