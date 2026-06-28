package com.aistudio.overread.bzvz.vision.language

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.tasks.await

object LanguageIdEngine {
    private val identifier = LanguageIdentification.getClient()

    suspend fun identifyLanguage(text: String, targetLanguage: String): LanguageIdResult {
        if (text.isBlank()) {
            return LanguageIdResult(
                success = true,
                detectedLanguage = null,
                confidence = null,
                isUnknown = true,
                isSameAsTarget = false,
                targetLanguage = targetLanguage,
                timestamp = System.currentTimeMillis()
            )
        }
        
        return try {
            val resultLanguages = identifier.identifyPossibleLanguages(text).await()
            
            if (resultLanguages.isNullOrEmpty() || resultLanguages.first().languageTag == "und") {
                LanguageIdResult(
                    success = true,
                    detectedLanguage = null,
                    confidence = null,
                    possibleLanguages = emptyList(),
                    isUnknown = true,
                    isSameAsTarget = false,
                    targetLanguage = targetLanguage,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                val primary = resultLanguages.first()
                val isLowConfidence = primary.confidence < 0.3f // Threshold
                
                LanguageIdResult(
                    success = true,
                    detectedLanguage = if (isLowConfidence) null else primary.languageTag,
                    confidence = if (isLowConfidence) null else primary.confidence,
                    possibleLanguages = resultLanguages.map { 
                        DetectedLanguageCandidate(it.languageTag, it.confidence)
                    },
                    isUnknown = isLowConfidence,
                    isSameAsTarget = if (isLowConfidence) false else primary.languageTag.equals(targetLanguage, ignoreCase = true),
                    targetLanguage = targetLanguage,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            LanguageIdResult(
                success = false,
                detectedLanguage = null,
                confidence = null,
                isUnknown = true,
                isSameAsTarget = false,
                targetLanguage = targetLanguage,
                timestamp = System.currentTimeMillis(),
                errorMessage = e.message
            )
        }
    }
}
