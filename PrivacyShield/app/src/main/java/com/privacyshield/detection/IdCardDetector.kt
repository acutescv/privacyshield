package com.privacyshield.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Detects ID cards using a YOLOv8n TFLite model.
 *
 * Model spec:
 *   Input:  [1, 320, 320, 3]  float32 RGB, normalized 0-1
 *   Output: [1, 5, 8400]      [cx, cy, w, h, confidence] per anchor
 *
 * To prepare the model:
 *   yolo export model=yolov8n.pt format=tflite imgsz=320
 * Or download a pre-converted model from Ultralytics.
 */
class IdCardDetector(private val context: Context) {

    companion object {
        private const val MODEL_FILE          = "models/yolov8n_id.tflite"
        private const val INPUT_SIZE          = 320
        private const val NUM_CHANNELS        = 3
        private const val CONFIDENCE_THRESHOLD = 0.50f
        private const val IOU_THRESHOLD        = 0.45f
        private const val BYTES_PER_FLOAT      = 4
    }

    // Pre-allocated buffers — reused every frame to avoid GC pressure
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * BYTES_PER_FLOAT)
        .apply { order(ByteOrder.nativeOrder()) }

    private val outputBuffer = Array(1) { Array(5) { FloatArray(8400) } }

    private val interpreter: Interpreter by lazy { buildInterpreter() }
    private val intPixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    private fun buildInterpreter(): Interpreter {
        val model = FileUtil.loadMappedFile(context, MODEL_FILE)
        val options = Interpreter.Options().apply {
            numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
            try {
                addDelegate(GpuDelegate(GpuDelegate.Options().apply {
                    setPrecisionLossAllowed(true) // FP16 for speed
                }))
            } catch (_: Exception) {
                // No GPU delegate — fall back to CPU silently
            }
        }
        return Interpreter(model, options)
    }

    /** Detect ID cards in the given frame. Call from background thread only. */
    fun detect(imageProxy: ImageProxy): List<CardBounds> {
        val bitmap = imageProxy.toBitmap()
        preprocess(bitmap)
        bitmap.recycle()

        interpreter.run(inputBuffer, outputBuffer)

        return postprocess(imageProxy.width, imageProxy.height)
    }

    private fun preprocess(source: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        scaled.getPixels(intPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        inputBuffer.rewind()
        for (pixel in intPixels) {
            inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f) // R
            inputBuffer.putFloat((pixel shr  8 and 0xFF) / 255.0f) // G
            inputBuffer.putFloat((pixel        and 0xFF) / 255.0f) // B
        }

        if (!scaled.sameAs(source)) scaled.recycle()
    }

    private fun postprocess(imgW: Int, imgH: Int): List<CardBounds> {
        val raw = mutableListOf<CardBounds>()

        for (i in 0 until 8400) {
            val confidence = outputBuffer[0][4][i]
            if (confidence < CONFIDENCE_THRESHOLD) continue

            val cx = outputBuffer[0][0][i]
            val cy = outputBuffer[0][1][i]
            val w  = outputBuffer[0][2][i]
            val h  = outputBuffer[0][3][i]

            raw += CardBounds(
                rect = RectF(
                    ((cx - w / 2) * imgW).coerceIn(0f, imgW.toFloat()),
                    ((cy - h / 2) * imgH).coerceIn(0f, imgH.toFloat()),
                    ((cx + w / 2) * imgW).coerceIn(0f, imgW.toFloat()),
                    ((cy + h / 2) * imgH).coerceIn(0f, imgH.toFloat())
                ),
                confidence = confidence
            )
        }
        return nonMaxSuppression(raw)
    }

    private fun nonMaxSuppression(boxes: List<CardBounds>): List<CardBounds> {
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val kept   = mutableListOf<CardBounds>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            sorted.removeAll { iou(best.rect, it.rect) > IOU_THRESHOLD }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val il = maxOf(a.left, b.left)
        val it = maxOf(a.top,  b.top)
        val ir = minOf(a.right, b.right)
        val ib = minOf(a.bottom, b.bottom)
        if (ir <= il || ib <= it) return 0f
        val inter = (ir - il) * (ib - it)
        return inter / (a.width() * a.height() + b.width() * b.height() - inter)
    }

    fun close() = interpreter.close()
}
