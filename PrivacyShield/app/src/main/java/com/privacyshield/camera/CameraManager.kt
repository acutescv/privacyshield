package com.privacyshield.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.privacyshield.detection.IdCardDetector
import com.privacyshield.detection.OcrEngine
import com.privacyshield.detection.SensitiveFieldClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val idCardDetector = IdCardDetector(context)
    private val ocrEngine = OcrEngine()
    private val classifier = SensitiveFieldClassifier()

    private val frameAnalyzer = FrameAnalyzer(
        idCardDetector, ocrEngine, classifier, scope
    )

    val detectionResults: StateFlow<*> = frameAnalyzer.detectionResults

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases(lifecycleOwner, previewView)
        }, context.mainExecutor)
    }

    private fun bindUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder()
            .setTargetResolution(Size(640, 480))
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            // KEEP_ONLY_LATEST = drop old frames if analyzer is busy
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            // Camera binding can fail if the device doesn't support requested config
            e.printStackTrace()
        }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        idCardDetector.close()
        ocrEngine.close()
    }
}
