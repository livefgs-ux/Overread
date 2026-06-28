package com.example.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processBitmap(bitmap: Bitmap): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            val blocks = result.textBlocks.map { block ->
                OcrTextBlock(
                    text = block.text,
                    boundingBox = block.boundingBox,
                    lines = block.lines.map { line ->
                        OcrLine(
                            text = line.text,
                            boundingBox = line.boundingBox,
                            elements = line.elements.map { element ->
                                OcrElement(
                                    text = element.text,
                                    boundingBox = element.boundingBox
                                )
                            }
                        )
                    }
                )
            }

            OcrResult(
                success = true,
                fullText = result.text,
                blocks = blocks,
                blockCount = blocks.size,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            OcrResult(
                success = false,
                fullText = "",
                blocks = emptyList(),
                blockCount = 0,
                timestamp = System.currentTimeMillis(),
                errorMessage = "Failed to process text recognition."
            )
        }
    }
}
