package com.aistudio.overread.bzvz.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * OCR Engine with multi-script support for webtoon translation.
 * Automatically detects which recognizer to use based on the detected language.
 * Supports: Latin, Chinese, Japanese, Korean, Devanagari scripts.
 */
object OcrEngine {

    private const val TAG = "OcrEngine"

    // Lazy-initialized recognizers for each script
    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val devanagariRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    /**
     * Language codes mapped to their primary script recognizer.
     * This helps us pick the best OCR engine for the detected language.
     */
    private val languageToScript = mapOf(
        // Latin-script languages
        "en" to Script.LATIN, "es" to Script.LATIN, "fr" to Script.LATIN,
        "de" to Script.LATIN, "it" to Script.LATIN, "pt" to Script.LATIN,
        "nl" to Script.LATIN, "pl" to Script.LATIN, "tr" to Script.LATIN,
        "vi" to Script.LATIN, "id" to Script.LATIN, "ms" to Script.LATIN,
        "tl" to Script.LATIN, "sv" to Script.LATIN, "da" to Script.LATIN,
        "no" to Script.LATIN, "fi" to Script.LATIN, "cs" to Script.LATIN,
        "hu" to Script.LATIN, "ro" to Script.LATIN, "sk" to Script.LATIN,
        "hr" to Script.LATIN, "sl" to Script.LATIN, "et" to Script.LATIN,
        "lv" to Script.LATIN, "lt" to Script.LATIN, "sq" to Script.LATIN,
        "mt" to Script.LATIN, "ga" to Script.LATIN, "cy" to Script.LATIN,
        // Chinese
        "zh" to Script.CHINESE, "zh-Hans" to Script.CHINESE, "zh-Hant" to Script.CHINESE,
        // Japanese
        "ja" to Script.JAPANESE,
        // Korean
        "ko" to Script.KOREAN,
        // Devanagari
        "hi" to Script.DEVANAGARI, "mr" to Script.DEVANAGARI, "ne" to Script.DEVANAGARI,
        "sa" to Script.DEVANAGARI
    )

    enum class Script {
        LATIN, CHINESE, JAPANESE, KOREAN, DEVANAGARI
    }

    /**
     * Process bitmap with the best recognizer for the given language.
     * If language is null or unknown, uses Latin as default (which handles many scripts).
     */
    suspend fun processBitmap(bitmap: Bitmap, detectedLanguage: String? = null): OcrResult {
        val script = detectedLanguage?.let { languageToScript[it] } ?: Script.LATIN
        val recognizer = getRecognizerForScript(script)

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

            Log.d(TAG, "OCR with script $script: ${blocks.size} blocks detected")

            OcrResult(
                success = true,
                fullText = result.text,
                blocks = blocks,
                blockCount = blocks.size,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed with script $script: ${e.message}")
            OcrResult(
                success = false,
                fullText = "",
                blocks = emptyList(),
                blockCount = 0,
                timestamp = System.currentTimeMillis(),
                errorMessage = "Failed to process text recognition: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Process bitmap trying multiple recognizers and return the best result.
     * Useful when language is unknown - picks the result with most text blocks.
     */
    suspend fun processBitmapMultiScript(bitmap: Bitmap): OcrResult {
        val scripts = listOf(Script.LATIN, Script.CHINESE, Script.JAPANESE, Script.KOREAN)
        var bestResult: OcrResult? = null

        for (script in scripts) {
            try {
                val recognizer = getRecognizerForScript(script)
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

                val ocrResult = OcrResult(
                    success = true,
                    fullText = result.text,
                    blocks = blocks,
                    blockCount = blocks.size,
                    timestamp = System.currentTimeMillis()
                )

                // Pick the result with the most blocks
                if (bestResult == null || ocrResult.blockCount > bestResult!!.blockCount) {
                    bestResult = ocrResult
                }

                // If we found substantial text, no need to try other scripts
                if (blocks.size >= 2) break

            } catch (e: Exception) {
                Log.e(TAG, "Multi-script OCR failed for $script: ${e.message}")
                continue
            }
        }

        return bestResult ?: OcrResult(
            success = false,
            fullText = "",
            blocks = emptyList(),
            blockCount = 0,
            timestamp = System.currentTimeMillis(),
            errorMessage = "All OCR recognizers failed."
        )
    }

    private fun getRecognizerForScript(script: Script): TextRecognizer {
        return when (script) {
            Script.CHINESE -> chineseRecognizer
            Script.JAPANESE -> japaneseRecognizer
            Script.KOREAN -> koreanRecognizer
            Script.DEVANAGARI -> devanagariRecognizer
            Script.LATIN -> latinRecognizer
        }
    }

    /**
     * Get the script type for a given language code
     */
    fun getScriptForLanguage(languageCode: String): Script {
        return languageToScript[languageCode] ?: Script.LATIN
    }
}
