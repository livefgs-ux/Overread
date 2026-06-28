package com.example.vision

import com.example.vision.language.LanguageIdEngine
import com.example.vision.language.LanguageIdResult
import com.example.vision.language.LanguageIdState
import com.example.vision.processing.ProcessedTextResult
import com.example.vision.processing.TextMerger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VisionManager {
    private val _ocrState = MutableStateFlow(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()

    private val _lastOcrResult = MutableStateFlow<OcrResult?>(null)
    val lastOcrResult: StateFlow<OcrResult?> = _lastOcrResult.asStateFlow()

    private val _lastProcessedResult = MutableStateFlow<ProcessedTextResult?>(null)
    val lastProcessedResult: StateFlow<ProcessedTextResult?> = _lastProcessedResult.asStateFlow()
    
    private val _languageIdState = MutableStateFlow(LanguageIdState.Idle)
    val languageIdState: StateFlow<LanguageIdState> = _languageIdState.asStateFlow()
    
    private val _lastLanguageIdResult = MutableStateFlow<LanguageIdResult?>(null)
    val lastLanguageIdResult: StateFlow<LanguageIdResult?> = _lastLanguageIdResult.asStateFlow()

    fun updateState(state: OcrState) {
        _ocrState.value = state
    }

    suspend fun processOcrResult(result: OcrResult, targetLanguage: String) {
        _lastOcrResult.value = result
        
        if (result.success && result.blockCount > 0) {
            _ocrState.value = OcrState.TextProcessing
            _languageIdState.value = LanguageIdState.Idle
            _lastLanguageIdResult.value = null
            com.example.translation.TranslationManager.reset()
            
            val processedOutput = withContext(Dispatchers.Default) {
                TextMerger.process(result)
            }
            
            _lastProcessedResult.value = processedOutput
            
            if (processedOutput.processedBlockCount > 0) {
                _ocrState.value = OcrState.Success
                
                // Run Language ID
                _languageIdState.value = LanguageIdState.Processing
                val textForLangId = processedOutput.fullProcessedText
                
                val langIdResult = withContext(Dispatchers.Default) {
                    LanguageIdEngine.identifyLanguage(textForLangId, targetLanguage)
                }
                
                _lastLanguageIdResult.value = langIdResult
                _languageIdState.value = when {
                    !langIdResult.success -> LanguageIdState.Failed
                    langIdResult.isUnknown -> LanguageIdState.Unknown
                    else -> LanguageIdState.Success
                }

                // Run Translation
                com.example.translation.TranslationManager.processTranslation(
                    processedOutput, langIdResult
                )

            } else {
                _ocrState.value = OcrState.NoTextFound
            }
        } else if (result.success && result.blockCount == 0) {
            _lastProcessedResult.value = null
            _ocrState.value = OcrState.NoTextFound
        } else {
            _lastProcessedResult.value = null
            _ocrState.value = OcrState.Failed
        }
    }
}
