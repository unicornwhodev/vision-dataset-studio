package com.unicornwhodev.visiondatasetstudio.core.geometry

/** One transform for ContentScale.Fit, its overlay and touch hit testing.
 * Zoom is around the viewport centre, like Compose's default graphicsLayer.
 */
data class ViewPoint(val x: Float, val y: Float)

data class ImageViewport(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    val isValid: Boolean get() = listOf(viewportWidth, viewportHeight, imageWidth, imageHeight, zoom).all { it.isFinite() && it > 0f } && panX.isFinite() && panY.isFinite()
    private val fit: Float get() = if (isValid) minOf(viewportWidth / imageWidth, viewportHeight / imageHeight) else 0f
    val width: Float get() = imageWidth * fit * zoom
    val height: Float get() = imageHeight * fit * zoom
    val left: Float get() = (viewportWidth - width) / 2f + panX
    val top: Float get() = (viewportHeight - height) / 2f + panY

    fun toScreen(x: Float, y: Float) = ViewPoint(left + x * width, top + y * height)

    /** Outside-image taps are ignored; only an ongoing drag can clamp to an image edge. */
    fun toImage(x: Float, y: Float, clamp: Boolean = false): ViewPoint? {
        if (!isValid || !x.isFinite() || !y.isFinite()) return null
        val nx = (x - left) / width
        val ny = (y - top) / height
        if (!clamp && (nx !in 0f..1f || ny !in 0f..1f)) return null
        return ViewPoint(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
    }
}
