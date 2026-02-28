package com.privacyshield.detection

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit Text Recognition + Barcode Scanning.
 * Both models run fully on-device (bundled in the app, no Play Services dependency).
 */
class OcrEngine {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient()

    /**
     * Run OCR + barcode detection on the full frame.
     * Optionally restrict to [cardRegions] bounding boxes (optimization).
     */
    suspend fun extractText(
        imageProxy: ImageProxy,
        cardRegions: List<CardBounds>
    ): List<OcrResult> {
        val inputImage = InputImage.fromMediaImage(
            imageProxy.image!!,
            imageProxy.imageInfo.rotationDegrees
        )

        val textResults   = runTextRecognition(inputImage)
        val barcodeResults = runBarcodeScanning(inputImage)

        // Merge; each text block / barcode becomes one OcrResult
        return textResults + barcodeResults
    }

    private suspend fun runTextRecognition(image: InputImage): List<OcrResult> =
        suspendCancellableCoroutine { cont ->
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val results = visionText.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            val bbox = line.boundingBox ?: return@mapNotNull null
                            OcrResult(
                                text       = line.text,
                                bounds     = RectF(bbox),
                                confidence = line.confidence ?: 0.7f
                                // text is NOT persisted beyond this frame
                            )
                        }
                    }
                    cont.resume(results)
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private suspend fun runBarcodeScanning(image: InputImage): List<OcrResult> =
        suspendCancellableCoroutine { cont ->
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val results = barcodes.mapNotNull { barcode ->
                        val bbox = barcode.boundingBox ?: return@mapNotNull null
                        OcrResult(
                            text       = barcode.rawValue ?: "",
                            bounds     = RectF(bbox),
                            confidence = 0.99f,
                            isQrCode   = barcode.format == Barcode.FORMAT_QR_CODE,
                            isBarcode  = barcode.format != Barcode.FORMAT_QR_CODE
                        )
                    }
                    cont.resume(results)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}
