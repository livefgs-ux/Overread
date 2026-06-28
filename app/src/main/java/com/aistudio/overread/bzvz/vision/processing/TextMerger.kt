package com.aistudio.overread.bzvz.vision.processing

import com.aistudio.overread.bzvz.vision.OcrResult

object TextMerger {
    fun merge(ocrResult: OcrResult): List<ProcessedTextBlock> {
        if (!ocrResult.success) return emptyList()

        // Naive implementation for Phase 5: 
        // Trust ML Kit's OcrTextBlock grouping as the primary block,
        // but merge its lines properly.
        // Future iterations can merge distinct OcrTextBlocks if they are very close.
        
        return ocrResult.blocks.mapNotNull { block ->
            if (block.lines.isEmpty()) return@mapNotNull null
            
            val originalText = block.text
            val normalizedText = TextNormalizer.normalize(originalText)
            
            if (normalizedText.isBlank()) return@mapNotNull null
            
            ProcessedTextBlock(
                originalText = originalText,
                normalizedText = normalizedText,
                boundingBox = block.boundingBox,
                sourceLineCount = block.lines.size
            )
        }
    }
    
    fun process(ocrResult: OcrResult): ProcessedTextResult {
        val mergedBlocks = merge(ocrResult)
        val fullProcessedText = mergedBlocks.joinToString("\n") { it.normalizedText }
        
        return ProcessedTextResult(
            success = ocrResult.success,
            blocks = mergedBlocks,
            fullProcessedText = fullProcessedText,
            originalBlockCount = ocrResult.blockCount,
            processedBlockCount = mergedBlocks.size,
            timestamp = System.currentTimeMillis(),
            errorMessage = ocrResult.errorMessage
        )
    }
}
