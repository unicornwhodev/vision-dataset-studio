package com.unicornwhodev.visiondatasetstudio.core.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * Normalized 2D coordinates in [0.0, 1.0], with top-left origin.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamp(): NormalizedPoint = NormalizedPoint(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f)
    )
}

/**
 * Normalized bounding box [xmin, ymin, xmax, ymax] in [0.0, 1.0].
 */
data class NormalizedRect(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float
) {
    val width: Float get() = max(0f, xmax - xmin)
    val height: Float get() = max(0f, ymax - ymin)
    val centerX: Float get() = xmin + width / 2f
    val centerY: Float get() = ymin + height / 2f

    fun clamp(): NormalizedRect {
        val minX = min(xmin, xmax).coerceIn(0f, 1f)
        val maxX = max(xmin, xmax).coerceIn(0f, 1f)
        val minY = min(ymin, ymax).coerceIn(0f, 1f)
        val maxY = max(ymin, ymax).coerceIn(0f, 1f)
        return NormalizedRect(minX, minY, maxX, maxY)
    }

    /**
     * Converts to COCO pixel coordinates [x_min, y_min, width, height].
     */
    fun toCocoPx(imgWidth: Int, imgHeight: Int): List<Double> {
        val c = clamp()
        val x = c.xmin * imgWidth
        val y = c.ymin * imgHeight
        val w = c.width * imgWidth
        val h = c.height * imgHeight
        return listOf(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
    }

    /**
     * Converts to YOLO normalized format [x_center, y_center, width, height].
     */
    fun toYolo(): List<Float> {
        val c = clamp()
        return listOf(c.centerX, c.centerY, c.width, c.height)
    }

    companion object {
        fun fromCocoPx(x: Double, y: Double, w: Double, h: Double, imgWidth: Int, imgHeight: Int): NormalizedRect {
            if (imgWidth <= 0 || imgHeight <= 0) return NormalizedRect(0f, 0f, 0f, 0f)
            val xmin = (x / imgWidth).toFloat()
            val ymin = (y / imgHeight).toFloat()
            val xmax = ((x + w) / imgWidth).toFloat()
            val ymax = ((y + h) / imgHeight).toFloat()
            return NormalizedRect(xmin, ymin, xmax, ymax).clamp()
        }

        fun fromYolo(xCenter: Float, yCenter: Float, w: Float, h: Float): NormalizedRect {
            val xmin = xCenter - w / 2f
            val ymin = yCenter - h / 2f
            val xmax = xCenter + w / 2f
            val ymax = yCenter + h / 2f
            return NormalizedRect(xmin, ymin, xmax, ymax).clamp()
        }
    }
}

/**
 * Geometric letterbox and scaling calculations for TFLite/LiteRT model inputs.
 */
object LetterboxMath {
    data class LetterboxResult(
        val scale: Float,
        val padX: Float,
        val padY: Float,
        val targetWidth: Int,
        val targetHeight: Int
    )

    fun calculateLetterbox(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): LetterboxResult {
        val scale = min(dstWidth.toFloat() / srcWidth, dstHeight.toFloat() / srcHeight)
        val scaledW = srcWidth * scale
        val scaledH = srcHeight * scale
        val padX = (dstWidth - scaledW) / 2f
        val padY = (dstHeight - scaledH) / 2f
        return LetterboxResult(scale, padX, padY, dstWidth, dstHeight)
    }

    /**
     * Maps a box detected inside letterboxed image back to [0,1] normalized original image space.
     */
    fun unletterboxBox(boxInLetterboxNorm: NormalizedRect, info: LetterboxResult): NormalizedRect {
        val dstW = info.targetWidth.toFloat()
        val dstH = info.targetHeight.toFloat()

        // Pixel coords on the letterbox canvas
        val pxMin = boxInLetterboxNorm.xmin * dstW
        val pxMax = boxInLetterboxNorm.xmax * dstW
        val pyMin = boxInLetterboxNorm.ymin * dstH
        val pyMax = boxInLetterboxNorm.ymax * dstH

        // Remove padding and invert scale
        val origPxMin = (pxMin - info.padX) / info.scale
        val origPxMax = (pxMax - info.padX) / info.scale
        val origPyMin = (pyMin - info.padY) / info.scale
        val origPyMax = (pyMax - info.padY) / info.scale

        val origW = (dstW - 2 * info.padX) / info.scale
        val origH = (dstH - 2 * info.padY) / info.scale

        return NormalizedRect(
            xmin = origPxMin / origW,
            ymin = origPyMin / origH,
            xmax = origPxMax / origW,
            ymax = origPyMax / origH
        ).clamp()
    }
}
