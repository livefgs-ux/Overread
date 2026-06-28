package com.example.vision.processing

import android.graphics.Rect

data class ProcessedTextResult(
    val success: Boolean,
    val blocks: List<ProcessedTextBlock>,
    val fullProcessedText: String,
    val originalBlockCount: Int,
    val processedBlockCount: Int,
    val timestamp: Long,
    val errorMessage: String? = null
)

data class ProcessedTextBlock(
    val originalText: String,
    val normalizedText: String,
    val boundingBox: Rect?,
    val sourceLineCount: Int
)
