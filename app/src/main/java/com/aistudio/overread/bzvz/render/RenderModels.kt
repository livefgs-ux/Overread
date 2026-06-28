package com.aistudio.overread.bzvz.render

import android.graphics.Rect
import android.graphics.RectF

data class RenderTextBox(
    val id: String,
    val translatedText: String,
    val originalText: String,
    val sourceBoundingBox: Rect?,
    val screenBoundingBox: RectF,
    val sourceLanguage: String?,
    val targetLanguage: String
)

data class RenderResult(
    val success: Boolean,
    val boxes: List<RenderTextBox>,
    val timestamp: Long,
    val errorMessage: String? = null
)

enum class RenderState {
    Idle,
    Rendering,
    Success,
    Skipped,
    Failed
}
