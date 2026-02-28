package com.privacyshield.blur

import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/**
 * Applies privacy masks to bitmap regions.
 *
 * Tries GPU-accelerated RenderScript first, falls back to software if unavailable.
 * All bitmaps are explicitly recycled after use — no memory leaks.
 */
class BlurEngine(context: Context) {

    private val rs: RenderScript? = runCatching { RenderScript.create(context) }.getOrNull()
    private val blurScript: ScriptIntrinsicBlur? = rs?.let {
        runCatching { ScriptIntrinsicBlur.create(it, Element.U8_4(it)) }.getOrNull()
    }

    fun applyMasks(
        source: Bitmap,
        regions: List<android.graphics.RectF>,
        mode: BlurMode
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        for (region in regions) {
            val rect = Rect(
                region.left.toInt().coerceAtLeast(0),
                region.top.toInt().coerceAtLeast(0),
                region.right.toInt().coerceAtMost(source.width),
                region.bottom.toInt().coerceAtMost(source.height)
            )
            if (rect.isEmpty) continue

            when (mode) {
                BlurMode.GAUSSIAN        -> applyGaussianBlur(canvas, source, rect)
                BlurMode.PIXELATE        -> applyPixelation(canvas, source, rect)
                BlurMode.BLACK_RECTANGLE -> applyBlackRect(canvas, rect)
            }
        }
        return result
    }

    private fun applyGaussianBlur(canvas: Canvas, source: Bitmap, rect: Rect) {
        val region = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())

        val blurred: Bitmap = if (rs != null && blurScript != null) {
            // RenderScript GPU path
            val input  = Allocation.createFromBitmap(rs, region)
            val output = Allocation.createTyped(rs, input.type)
            blurScript.setRadius(25f)
            blurScript.setInput(input)
            blurScript.forEach(output)
            val out = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
            output.copyTo(out)
            input.destroy(); output.destroy()
            out
        } else {
            // Software fallback: BlurMaskFilter
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
            }
            val out = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(region, 0f, 0f, paint)
            out
        }

        canvas.drawBitmap(blurred, null, rect, null)
        blurred.recycle()
        region.recycle()
    }

    private fun applyPixelation(canvas: Canvas, source: Bitmap, rect: Rect) {
        val pixelSize = 12
        val region  = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val wSmall  = (region.width  / pixelSize).coerceAtLeast(1)
        val hSmall  = (region.height / pixelSize).coerceAtLeast(1)
        val small   = Bitmap.createScaledBitmap(region, wSmall, hSmall, false)
        val pixelated = Bitmap.createScaledBitmap(small, region.width, region.height, false)

        canvas.drawBitmap(pixelated, null, rect, null)
        pixelated.recycle(); small.recycle(); region.recycle()
    }

    private fun applyBlackRect(canvas: Canvas, rect: Rect) {
        canvas.drawRect(rect, Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        })
    }

    fun destroy() {
        blurScript?.destroy()
        rs?.destroy()
    }
}
