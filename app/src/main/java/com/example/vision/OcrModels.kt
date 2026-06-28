package com.example.vision

import android.graphics.Rect

enum class OcrState {
    Idle,
    Processing,
    TextProcessing,
    Success,
    Failed,
    NoTextFound
}

data class OcrResult(
    val success: Boolean,
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val blockCount: Int,
    val timestamp: Long,
    val errorMessage: String? = null
)

data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val elements: List<OcrElement>
)

data class OcrElement(
    val text: String,
    val boundingBox: Rect?
)
