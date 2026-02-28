package com.privacyshield.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.privacyshield.detection.DetectionResult
import com.privacyshield.detection.IdCardDetector
import com.privacyshield.detection.OcrEngine
import com.privacyshield.detection.SensitiveFieldClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX ImageAnalysis.Analyzer.
 *
 * Pipeline per frame:
 *   YOLOv8n detection → (if card found) ML Kit OCR → regex classifier → emit results
 *
 * Frame dropping strategy: if the previous frame is still being processed,
 * drop the incoming frame immediately. This ensures we never fall behind
 * real-time while maintaining a clean backpressure.
 */
class FrameAnalyzer(
    private val idCardDetector: IdCardDetector,
    private val ocrEngine: OcrEngine,
    private val sensitiveFieldClassifier: SensitiveFieldClassifier,
    private val scope: CoroutineScope
) : ImageAnalysis.Analyzer {

    private val _detectionResults = MutableStateFlow<List<DetectionResult>>(emptyList())
    val detectionResults: StateFlow<List<DetectionResult>> = _detectionResults

    private val isProcessing = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        // Drop frame immediately if still processing previous
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val cardBounds = idCardDetector.detect(imageProxy)

                if (cardBounds.isNotEmpty()) {
                    val ocrResults = ocrEngine.extractText(imageProxy, cardBounds)
                    val sensitiveFields = sensitiveFieldClassifier.classify(ocrResults)
                    _detectionResults.value = sensitiveFields
                } else {
                    // Fade out quickly when card leaves frame
                    if (_detectionResults.value.isNotEmpty()) {
                        _detectionResults.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                // Never crash on a bad frame
                _detectionResults.value = emptyList()
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }
}
