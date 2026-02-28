package com.privacyshield.detection

import android.graphics.RectF

class SensitiveFieldClassifier {

    companion object {
        // ── Regex patterns ────────────────────────────────────────────────────
        /** ID / passport numbers: 6–20 digit sequences */
        private val ID_NUMBER_REGEX = Regex("""\b\d{6,20}\b""")

        /** Dates: DD/MM/YYYY, MM-DD-YYYY, YYYY.MM.DD, etc. */
        private val DOB_REGEX = Regex(
            """\b(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4}|\d{4}[\/\-\.]\d{1,2}[\/\-\.]\d{1,2})\b"""
        )

        /** Names: 2–4 capitalised words (First Last or First Middle Last) */
        private val NAME_REGEX = Regex("""^[A-Z][a-záàâäæãåāéèêëēėęîïíīįìôöòóœøōõùúûüůūÿýñç]+(?: [A-Z][a-záàâäæãåāéèêëēėęîïíīįìôöòóœøōõùúûüůūÿýñç]+){1,3}$""")

        /** Addresses: number + street keyword */
        private val ADDRESS_REGEX = Regex(
            """\d+\s+[A-Z][a-z]+.{0,30}(Street|St\.?|Avenue|Ave\.?|Road|Rd\.?|Boulevard|Blvd\.?|Lane|Ln\.?|Drive|Dr\.?|Court|Ct\.?|Way|Place|Pl\.?)""",
            RegexOption.IGNORE_CASE
        )
    }

    fun classify(ocrResults: List<OcrResult>): List<DetectionResult> =
        ocrResults.flatMap { result -> classifyOne(result) }

    private fun classifyOne(result: OcrResult): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()

        when {
            result.isQrCode  -> detections += makeResult(FieldType.QR_CODE,      result, 0.99f)
            result.isBarcode -> detections += makeResult(FieldType.BARCODE,       result, 0.99f)

            ID_NUMBER_REGEX.containsMatchIn(result.text) ->
                detections += makeResult(FieldType.ID_NUMBER,    result, 0.90f)

            DOB_REGEX.containsMatchIn(result.text) ->
                detections += makeResult(FieldType.DATE_OF_BIRTH, result, 0.85f)

            NAME_REGEX.matches(result.text.trim()) ->
                detections += makeResult(FieldType.FULL_NAME,    result, 0.75f)

            ADDRESS_REGEX.containsMatchIn(result.text) ->
                detections += makeResult(FieldType.ADDRESS,      result, 0.80f)
        }
        return detections
    }

    private fun makeResult(type: FieldType, src: OcrResult, conf: Float) =
        DetectionResult(
            fieldType    = type,
            boundingBox  = src.bounds,
            confidence   = conf
            // NOTE: We intentionally do NOT store src.text here
        )
}
