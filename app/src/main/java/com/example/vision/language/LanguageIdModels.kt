package com.example.vision.language

import android.graphics.Rect

enum class LanguageIdState {
    Idle,
    Processing,
    Success,
    Unknown,
    Failed
}

data class LanguageIdResult(
    val success: Boolean,
    val detectedLanguage: String?,
    val confidence: Float?,
    val possibleLanguages: List<DetectedLanguageCandidate> = emptyList(),
    val isUnknown: Boolean,
    val isSameAsTarget: Boolean,
    val targetLanguage: String,
    val timestamp: Long,
    val errorMessage: String? = null
)

data class DetectedLanguageCandidate(
    val languageCode: String,
    val confidence: Float
)
