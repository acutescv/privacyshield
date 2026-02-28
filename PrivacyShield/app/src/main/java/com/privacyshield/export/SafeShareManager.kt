package com.privacyshield.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.privacyshield.blur.BlurEngine
import com.privacyshield.blur.BlurMode
import com.privacyshield.detection.DetectionResult
import com.privacyshield.detection.FieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class SharePurpose(
    val label: String,
    val requiredFields: Set<FieldType>
) {
    SCHOOL_REGISTRATION("School Registration", setOf(FieldType.FULL_NAME, FieldType.DATE_OF_BIRTH)),
    BANK_ACCOUNT("Bank Account Opening",       setOf(FieldType.FULL_NAME, FieldType.ID_NUMBER)),
    GENERAL("General Purpose",                 setOf(FieldType.FULL_NAME)),
    CUSTOM("Custom",                           emptySet())
}

class SafeShareManager(private val context: Context) {

    private val blurEngine = BlurEngine(context)

    /**
     * Produce a masked bitmap. Fields NOT in [purpose.requiredFields] are blurred.
     * The result is a fresh bitmap; the [source] is not modified.
     */
    suspend fun createMaskedBitmap(
        source: Bitmap,
        detections: List<DetectionResult>,
        purpose: SharePurpose,
        blurMode: BlurMode = BlurMode.BLACK_RECTANGLE
    ): Bitmap = withContext(Dispatchers.Default) {
        val toMask = detections
            .filter { it.fieldType !in purpose.requiredFields }
            .map { it.boundingBox }

        blurEngine.applyMasks(source, toMask, blurMode)
    }

    /**
     * Save masked bitmap as a temporary JPEG.
     * The file lives in the app's cache dir and is cleared on app exit.
     */
    suspend fun exportAsJpeg(bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "safe_share").apply { mkdirs() }
        val file     = File(cacheDir, "masked_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Delete all exported files from cache. */
    fun clearExports() {
        File(context.cacheDir, "safe_share").deleteRecursively()
    }

    fun destroy() = blurEngine.destroy()
}
