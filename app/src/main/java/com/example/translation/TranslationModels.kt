package com.example.translation

import android.graphics.Rect

enum class TranslationState {
    Idle,
    CheckingLanguage,
    ModelRequired,
    DownloadingModel,
    Translating,
    Success,
    Skipped,
    Failed
}

enum class TranslationSkippedReason {
    NO_TEXT,
    UNKNOWN_SOURCE_LANGUAGE,
    SAME_AS_TARGET,
    UNSUPPORTED_LANGUAGE,
    MODEL_NOT_READY,
    LOW_CONFIDENCE
}

data class TranslationResult(
    val success: Boolean,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val blocks: List<TranslatedTextBlock>,
    val fullTranslatedText: String,
    val modelDownloadRequired: Boolean = false,
    val modelDownloaded: Boolean = false,
    val skippedReason: TranslationSkippedReason? = null,
    val timestamp: Long,
    val errorMessage: String? = null
)

data class TranslatedTextBlock(
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect?,
    val sourceLanguage: String?,
    val targetLanguage: String
)
