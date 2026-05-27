package com.dualshot.recorder.data.camera

/**
 * Converts YUV_420_888 frames to NV12 using pre-allocated buffers and LUTs.
 * Crop regions account for sensor [rotationDegrees] and [targetAspect].
 */
class YuvToNV12Converter(
    private val dstWidth: Int,
    private val dstHeight: Int,
    private val targetAspect: TargetAspect,
    private var rotationDegrees: Int = 0
) {
    enum class TargetAspect {
        /** 9:16 portrait output. */
        VERTICAL,

        /** 16:9 landscape output. */
        HORIZONTAL
    }

    /** Pre-allocated NV12 output. Valid until the next [convert] call. */
    val outputBuffer: ByteArray = ByteArray(dstWidth * dstHeight * 3 / 2)

    private var srcCropX = 0
    private var srcCropY = 0
    private var cropW = 0
    private var cropH = 0
    private var xLut: IntArray? = null
    private var yLut: IntArray? = null
    private var uvXLut: IntArray? = null
    private var uvYLut: IntArray? = null
    private var lutSrcW = 0
    private var lutSrcH = 0

    fun setRotation(degrees: Int) {
        val normalized = ((degrees % 360) + 360) % 360
        if (rotationDegrees != normalized) {
            rotationDegrees = normalized
            invalidateLuts()
        }
    }

    /**
     * Converts a copied YUV snapshot into [outputBuffer].
     */
    fun convert(planes: YuvFrameSnapshot.PlaneBuffers): ByteArray {
        val srcW = planes.width
        val srcH = planes.height
        ensureLuts(srcW, srcH)

        val yBuf = planes.y
        val uBuf = planes.u
        val vBuf = planes.v
        val yStride = planes.yRowStride
        val uvStride = planes.uvRowStride
        val uvPixel = planes.uvPixelStride

        return convertInternal(yBuf, uBuf, vBuf, yStride, uvStride, uvPixel, srcW, srcH)
    }

    private fun ensureLuts(srcW: Int, srcH: Int) {
        if (lutSrcW != srcW || lutSrcH != srcH) {
            invalidateLuts()
            lutSrcW = srcW
            lutSrcH = srcH
        }
        if (xLut == null) buildLuts(srcW, srcH)
    }

    private fun convertInternal(
        yBuf: ByteArray,
        uBuf: ByteArray,
        vBuf: ByteArray,
        yStride: Int,
        uvStride: Int,
        uvPixel: Int,
        srcW: Int,
        srcH: Int
    ): ByteArray {

        // Fast path: no crop/scale (common when analysis is 1280×720 and rotation is 90/270).
        // Avoid per-pixel absolute ByteBuffer.get(index), which is extremely slow in Kotlin.
        if (srcCropX == 0 &&
            srcCropY == 0 &&
            cropW == srcW &&
            cropH == srcH &&
            dstWidth == srcW &&
            dstHeight == srcH
        ) {
            var out = 0
            val yLimit = yBuf.size
            for (row in 0 until dstHeight) {
                val base = row * yStride
                for (col in 0 until dstWidth) {
                    val idx = (base + col).coerceIn(0, yLimit - 1)
                    outputBuffer[out++] = yBuf[idx]
                }
            }
            val uvDstW = dstWidth / 2
            val uvDstH = dstHeight / 2
            val uLimit = uBuf.size
            val vLimit = vBuf.size
            for (row in 0 until uvDstH) {
                val base = row * uvStride
                for (col in 0 until uvDstW) {
                    val idx = base + col * uvPixel
                    val clipped = idx.coerceIn(0, minOf(uLimit, vLimit) - 1)
                    outputBuffer[out++] = uBuf[clipped]
                    outputBuffer[out++] = vBuf[clipped]
                }
            }
            return outputBuffer
        }

        var out = 0
        val xL = xLut!!
        val yL = yLut!!
        val yLimit = yBuf.size
        for (row in 0 until dstHeight) {
            val srcRow = srcCropY + yL[row]
            val base = srcRow * yStride + srcCropX
            for (col in 0 until dstWidth) {
                val idx = (base + xL[col]).coerceIn(0, yLimit - 1)
                outputBuffer[out++] = yBuf[idx]
            }
        }

        val uvXL = uvXLut!!
        val uvYL = uvYLut!!
        val uvCropX2 = srcCropX / 2
        val uvCropY2 = srcCropY / 2
        val uvDstW = dstWidth / 2
        val uvDstH = dstHeight / 2
        val uLimit = uBuf.size
        val vLimit = vBuf.size

        for (row in 0 until uvDstH) {
            val srcRow = uvCropY2 + uvYL[row]
            val base = srcRow * uvStride + uvCropX2 * uvPixel
            for (col in 0 until uvDstW) {
                val uvIdx = base + uvXL[col] * uvPixel
                val clipped = uvIdx.coerceIn(0, minOf(uLimit, vLimit) - 1)
                outputBuffer[out++] = uBuf[clipped]
                outputBuffer[out++] = vBuf[clipped]
            }
        }

        return outputBuffer
    }

    private fun invalidateLuts() {
        xLut = null
        yLut = null
        uvXLut = null
        uvYLut = null
    }

    private fun buildLuts(srcW: Int, srcH: Int) {
        val rotated = rotationDegrees == 90 || rotationDegrees == 270

        when (targetAspect) {
            TargetAspect.VERTICAL -> {
                if (rotated) {
                    // Portrait strip inside landscape sensor buffer (e.g. 1280×720).
                    cropH = srcH
                    cropW = (srcH * 9f / 16f).toInt().coerceAtMost(srcW)
                    cropW = cropW and 0xFFFFFFFE.toInt()
                    centerCropX(srcW, cropW)
                    srcCropY = 0
                } else {
                    cropW = srcW
                    cropH = (srcW * 16f / 9f).toInt().coerceAtMost(srcH)
                    cropH = cropH and 0xFFFFFFFE.toInt()
                    srcCropX = 0
                    centerCropY(srcH, cropH)
                }
            }
            TargetAspect.HORIZONTAL -> {
                if (rotated) {
                    // Full landscape frame; muxer orientation hint stays 0°.
                    cropW = srcW
                    cropH = srcH
                    srcCropX = 0
                    srcCropY = 0
                } else {
                    cropH = srcH
                    cropW = (srcH * 16f / 9f).toInt().coerceAtMost(srcW)
                    cropW = cropW and 0xFFFFFFFE.toInt()
                    centerCropX(srcW, cropW)
                    srcCropY = 0
                }
            }
        }

        xLut = IntArray(dstWidth) { col -> col * cropW / dstWidth }
        yLut = IntArray(dstHeight) { row -> row * cropH / dstHeight }
        uvXLut = IntArray(dstWidth / 2) { col -> col * (cropW / 2) / (dstWidth / 2) }
        uvYLut = IntArray(dstHeight / 2) { row -> row * (cropH / 2) / (dstHeight / 2) }
    }

    private fun centerCropX(srcW: Int, cropWidth: Int) {
        srcCropX = ((srcW - cropWidth) / 2) and 0xFFFFFFFE.toInt()
    }

    private fun centerCropY(srcH: Int, cropHeight: Int) {
        srcCropY = ((srcH - cropHeight) / 2) and 0xFFFFFFFE.toInt()
    }
}
