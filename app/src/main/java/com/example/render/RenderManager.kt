package com.example.render

import com.example.translation.TranslationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object RenderManager {
    private val _renderState = MutableStateFlow(RenderState.Idle)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    private val _lastRenderResult = MutableStateFlow<RenderResult?>(null)
    val lastRenderResult: StateFlow<RenderResult?> = _lastRenderResult.asStateFlow()

    fun reset() {
        _renderState.value = RenderState.Idle
        _lastRenderResult.value = null
    }

    fun processTranslation(
        translationResult: TranslationResult,
        captureWidth: Int?,
        captureHeight: Int?,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (!translationResult.success || translationResult.blocks.isEmpty()) {
            _renderState.value = RenderState.Skipped
            _lastRenderResult.value = RenderResult(
                success = true,
                boxes = emptyList(),
                timestamp = System.currentTimeMillis()
            )
            return
        }

        _renderState.value = RenderState.Rendering

        try {
            val cWidth = captureWidth ?: screenWidth
            val cHeight = captureHeight ?: screenHeight

            val boxes = translationResult.blocks.mapNotNull { block ->
                if (block.boundingBox == null) return@mapNotNull null

                val screenBounds = OverlayBoxCalculator.calculateScreenBounds(
                    block.boundingBox,
                    cWidth,
                    cHeight,
                    screenWidth,
                    screenHeight
                )
                
                // If box is too small, maybe skip or just render it. We'll render it simple for Phase 7
                RenderTextBox(
                    id = UUID.randomUUID().toString(),
                    translatedText = block.translatedText,
                    originalText = block.originalText,
                    sourceBoundingBox = block.boundingBox,
                    screenBoundingBox = screenBounds,
                    sourceLanguage = block.sourceLanguage,
                    targetLanguage = block.targetLanguage
                )
            }

            _lastRenderResult.value = RenderResult(
                success = true,
                boxes = boxes,
                timestamp = System.currentTimeMillis()
            )
            _renderState.value = RenderState.Success

        } catch (e: Exception) {
            _lastRenderResult.value = RenderResult(
                success = false,
                boxes = emptyList(),
                timestamp = System.currentTimeMillis(),
                errorMessage = e.localizedMessage
            )
            _renderState.value = RenderState.Failed
        }
    }
}
