package com.dualshot.recorder.data.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Owns a copy of a YUV_420_888 frame so [ImageProxy] can be closed immediately
 * and heavy work can run on background executors.
 */
class YuvFrameSnapshot private constructor(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNs: Long,
    val y: ByteArray,
    val yRowStride: Int,
    val u: ByteArray,
    val v: ByteArray,
    val uvRowStride: Int,
    val uvPixelStride: Int
) {
    fun planeBuffers(): PlaneBuffers = PlaneBuffers(
        y = y,
        yRowStride = yRowStride,
        u = u,
        v = v,
        uvRowStride = uvRowStride,
        uvPixelStride = uvPixelStride,
        width = width,
        height = height
    )

    data class PlaneBuffers(
        val y: ByteArray,
        val yRowStride: Int,
        val u: ByteArray,
        val v: ByteArray,
        val uvRowStride: Int,
        val uvPixelStride: Int,
        val width: Int,
        val height: Int
    )

    companion object {
        fun capture(imageProxy: ImageProxy): YuvFrameSnapshot? {
            val image = imageProxy.image ?: return null
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val w = image.width
            val h = image.height
            val yStride = yPlane.rowStride
            val uvStride = uPlane.rowStride
            val uvPixel = uPlane.pixelStride

            val yBuf = yPlane.buffer.duplicate().apply { rewind() }
            val uBuf = uPlane.buffer.duplicate().apply { rewind() }
            val vBuf = vPlane.buffer.duplicate().apply { rewind() }

            val yBytes = ByteArray(yStride * h)
            val uBytes = ByteArray(uvStride * (h / 2))
            val vBytes = ByteArray(uvStride * (h / 2))

            copyPlane(yBuf, yBytes, yStride * h)
            copyPlane(uBuf, uBytes, uBytes.size.coerceAtMost(uBuf.remaining()))
            copyPlane(vBuf, vBytes, vBytes.size.coerceAtMost(vBuf.remaining()))

            return YuvFrameSnapshot(
                width = w,
                height = h,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                timestampNs = imageProxy.imageInfo.timestamp,
                y = yBytes,
                yRowStride = yStride,
                u = uBytes,
                v = vBytes,
                uvRowStride = uvStride,
                uvPixelStride = uvPixel
            )
        }

        private fun copyPlane(src: ByteBuffer, dst: ByteArray, length: Int) {
            src.get(dst, 0, length.coerceAtMost(dst.size).coerceAtMost(src.remaining()))
        }
    }
}
