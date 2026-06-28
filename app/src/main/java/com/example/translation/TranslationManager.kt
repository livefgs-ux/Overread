package com.example.translation

import com.example.vision.language.LanguageIdResult
import com.example.vision.processing.ProcessedTextResult
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranslationManager {
    private val _translationState = MutableStateFlow(TranslationState.Idle)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()
    
    private val _lastTranslationResult = MutableStateFlow<TranslationResult?>(null)
    val lastTranslationResult: StateFlow<TranslationResult?> = _lastTranslationResult.asStateFlow()

    fun reset() {
         _translationState.value = TranslationState.Idle
         _lastTranslationResult.value = null
    }

    suspend fun processTranslation(
        processedText: ProcessedTextResult,
        languageIdResult: LanguageIdResult
    ) {
        _lastTranslationResult.value = null
        _translationState.value = TranslationState.CheckingLanguage

        if (!processedText.success || processedText.blocks.isEmpty()) {
            skipTranslation(TranslationSkippedReason.NO_TEXT, languageIdResult)
            return
        }

        if (!languageIdResult.success || languageIdResult.isUnknown || languageIdResult.detectedLanguage.isNullOrEmpty()) {
            skipTranslation(TranslationSkippedReason.UNKNOWN_SOURCE_LANGUAGE, languageIdResult)
            return
        }

        if (languageIdResult.isSameAsTarget) {
            skipTranslation(TranslationSkippedReason.SAME_AS_TARGET, languageIdResult)
            return
        }

        val sourceLang = languageIdResult.detectedLanguage
        val targetLang = languageIdResult.targetLanguage

        val srcMlKit = TranslateLanguage.fromLanguageTag(sourceLang)
        val tgtMlKit = TranslateLanguage.fromLanguageTag(targetLang)
        if (srcMlKit == null || tgtMlKit == null) {
            skipTranslation(TranslationSkippedReason.UNSUPPORTED_LANGUAGE, languageIdResult)
            return
        }

        com.example.translation.model.TranslationModelManager.checkModelStatus(sourceLang, targetLang)
        val isReady = com.example.translation.model.TranslationModelManager.modelStatus.value.isReady

        if (!isReady) {
            _translationState.value = TranslationState.ModelRequired
            skipTranslation(TranslationSkippedReason.MODEL_NOT_READY, languageIdResult)
            return
        }

        try {
            val translatedBlocks = LocalMlKitTranslator.translateBlocks(
                blocks = processedText.blocks,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang
            )
            
            _translationState.value = TranslationState.Translating
            
            val fullTranslated = translatedBlocks.joinToString("\n") { it.translatedText }
            
            _lastTranslationResult.value = TranslationResult(
                success = true,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                blocks = translatedBlocks,
                fullTranslatedText = fullTranslated,
                modelDownloaded = true,
                skippedReason = null,
                timestamp = System.currentTimeMillis()
            )
            _translationState.value = TranslationState.Success

        } catch (e: Exception) {
            _lastTranslationResult.value = TranslationResult(
                success = false,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                blocks = emptyList(),
                fullTranslatedText = "",
                skippedReason = null,
                timestamp = System.currentTimeMillis(),
                errorMessage = "Translation encountered an error. Check if the model is downloaded."
            )
            _translationState.value = TranslationState.Failed
        }
    }

    private fun skipTranslation(reason: TranslationSkippedReason, languageInfo: LanguageIdResult) {
        _lastTranslationResult.value = TranslationResult(
            success = true,
            sourceLanguage = languageInfo.detectedLanguage,
            targetLanguage = languageInfo.targetLanguage,
            blocks = emptyList(),
            fullTranslatedText = "",
            skippedReason = reason,
            timestamp = System.currentTimeMillis()
        )
        _translationState.value = TranslationState.Skipped
    }
}
