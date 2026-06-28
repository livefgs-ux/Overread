package com.example.translation.model

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranslationModelManager {
    private val modelManager = RemoteModelManager.getInstance()
    
    private val _modelStatus = MutableStateFlow(
        TranslationModelStatus(
            sourceLanguage = null,
            targetLanguage = "en",
            isReady = false,
            isDownloading = false,
            downloadFailed = false,
            timestamp = System.currentTimeMillis()
        )
    )
    val modelStatus: StateFlow<TranslationModelStatus> = _modelStatus.asStateFlow()

    suspend fun checkModelStatus(sourceLang: String?, targetLang: String) {
        if (sourceLang == null) {
            updateStatus(sourceLang, targetLang, false, false, false, null)
            return
        }

        if (sourceLang == targetLang) {
            updateStatus(sourceLang, targetLang, true, false, false, null)
            return
        }

        val srcMlKit = TranslateLanguage.fromLanguageTag(sourceLang)
        val tgtMlKit = TranslateLanguage.fromLanguageTag(targetLang)
        
        if (srcMlKit == null || tgtMlKit == null) {
            updateStatus(sourceLang, targetLang, false, false, false, "Unsupported language")
            return
        }

        val srcModel = TranslateRemoteModel.Builder(srcMlKit).build()
        val tgtModel = TranslateRemoteModel.Builder(tgtMlKit).build()

        try {
            val srcDownloaded = modelManager.isModelDownloaded(srcModel).await()
            val tgtDownloaded = modelManager.isModelDownloaded(tgtModel).await()

            updateStatus(
                sourceLang, 
                targetLang, 
                isReady = srcDownloaded && tgtDownloaded, 
                isDownloading = false, 
                downloadFailed = false, 
                errorMessage = null
            )
        } catch (e: Exception) {
            updateStatus(sourceLang, targetLang, false, false, true, "Failed to verify local model.")
        }
    }

    suspend fun downloadModel(sourceLang: String, targetLang: String) {
        val srcMlKit = TranslateLanguage.fromLanguageTag(sourceLang)
        val tgtMlKit = TranslateLanguage.fromLanguageTag(targetLang)
        
        if (srcMlKit == null || tgtMlKit == null) {
            updateStatus(sourceLang, targetLang, false, false, true, "Unsupported language")
            return
        }

        val srcModel = TranslateRemoteModel.Builder(srcMlKit).build()
        val tgtModel = TranslateRemoteModel.Builder(tgtMlKit).build()

        updateStatus(sourceLang, targetLang, false, true, false, null)

        try {
            val conditions = DownloadConditions.Builder().build()
            
            val srcDownloaded = modelManager.isModelDownloaded(srcModel).await()
            if (!srcDownloaded) {
                withContext(Dispatchers.IO) {
                    modelManager.download(srcModel, conditions).await()
                }
            }
            
            val tgtDownloaded = modelManager.isModelDownloaded(tgtModel).await()
            if (!tgtDownloaded) {
                withContext(Dispatchers.IO) {
                    modelManager.download(tgtModel, conditions).await()
                }
            }

            updateStatus(sourceLang, targetLang, true, false, false, null)
        } catch (e: Exception) {
            updateStatus(sourceLang, targetLang, false, false, true, "Failed to download language model. Check internet connection.")
        }
    }

    private fun updateStatus(
        source: String?, 
        target: String, 
        isReady: Boolean, 
        isDownloading: Boolean, 
        downloadFailed: Boolean, 
        errorMessage: String?
    ) {
        _modelStatus.value = TranslationModelStatus(
            sourceLanguage = source,
            targetLanguage = target,
            isReady = isReady,
            isDownloading = isDownloading,
            downloadFailed = downloadFailed,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        )
    }
}
