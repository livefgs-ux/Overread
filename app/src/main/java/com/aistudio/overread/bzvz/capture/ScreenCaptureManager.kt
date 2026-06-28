package com.aistudio.overread.bzvz.capture

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenCaptureManager {
    var permissionResultData: Intent? = null
    var permissionResultCode: Int = 0
    
    val isPrepared: Boolean get() = permissionResultData != null

    val captureState = MutableStateFlow(CaptureState.Idle)
    val captureStage = MutableStateFlow(CaptureStage.Idle)
    val lastCaptureResult = MutableStateFlow<CaptureResult?>(null)

    fun prepare(resultCode: Int, data: Intent) {
        permissionResultCode = resultCode
        permissionResultData = data
        captureState.value = CaptureState.ReadyForOneCapture
        captureStage.value = CaptureStage.PermissionGranted
        lastCaptureResult.value = null
    }

    fun getPermissionData(): Pair<Int, Intent?> {
        val code = permissionResultCode
        val data = permissionResultData
        return Pair(code, data)
    }

    fun clear() {
        permissionResultData = null
        captureState.value = CaptureState.Idle
        captureStage.value = CaptureStage.Idle
    }
}
