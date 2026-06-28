package com.aistudio.overread.bzvz.translation.model

data class TranslationModelStatus(
    val sourceLanguage: String?,
    val targetLanguage: String,
    val isReady: Boolean,
    val isDownloading: Boolean,
    val downloadFailed: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long
)

enum class ModelDownloadState {
    Idle,
    Checking,
    Required,
    Downloading,
    Ready,
    Failed
}
