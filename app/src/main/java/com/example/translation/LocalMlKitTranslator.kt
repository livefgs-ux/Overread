package com.example.translation

import com.example.vision.processing.ProcessedTextBlock
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalMlKitTranslator {
    
    suspend fun translateBlocks(
        blocks: List<ProcessedTextBlock>,
        sourceLanguage: String,
        targetLanguage: String
    ): List<TranslatedTextBlock> {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguage)
        val target = TranslateLanguage.fromLanguageTag(targetLanguage)
        
        if (source == null || target == null) {
            throw IllegalArgumentException("Unsupported language")
        }
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
            
        val translator = Translation.getClient(options)
        
        return try {
            blocks.map { block ->
                val translated = if (block.normalizedText.isNotBlank()) {
                     withContext(Dispatchers.IO) { translator.translate(block.normalizedText).await() }
                } else {
                    ""
                }
                TranslatedTextBlock(
                    originalText = block.normalizedText,
                    translatedText = translated,
                    boundingBox = block.boundingBox,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
            }
        } finally {
            translator.close() // Close translator after use to free resources
        }
    }
}
