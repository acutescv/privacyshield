package com.privacyshield.detection

import android.graphics.RectF

enum class FieldType(val displayName: String, val priority: Int) {
    ID_NUMBER("ID Number", 1),
    DATE_OF_BIRTH("Date of Birth", 2),
    FULL_NAME("Full Name", 3),
    ADDRESS("Address", 4),
    BARCODE("Barcode", 1),
    QR_CODE("QR Code", 1),
    UNKNOWN("Unknown", 5)
}

data class CardBounds(
    val rect: RectF,
    val confidence: Float,
    val rotation: Float = 0f
)

data class OcrResult(
    val text: String,
    val bounds: RectF,
    val confidence: Float,
    val isBarcode: Boolean = false,
    val isQrCode: Boolean = false
)

data class DetectionResult(
    val fieldType: FieldType,
    val boundingBox: RectF,
    val confidence: Float,
    // Never store actual text — only use for UI label
    val fieldLabel: String = fieldType.displayName
)
