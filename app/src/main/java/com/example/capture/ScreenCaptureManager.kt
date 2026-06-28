package com.example.capture

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScreenCaptureManager {
    var permissionResultData: Intent? = null
    var permissionResultCode: Int = 0

    val captureState = MutableStateFlow(CaptureState.Idle)
    val lastCaptureResult = MutableStateFlow<CaptureResult?>(null)

    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests = _captureRequests.asSharedFlow()

    fun prepare(resultCode: Int, data: Intent) {
        permissionResultCode = resultCode
        permissionResultData = data
        captureState.value = CaptureState.Prepared
    }

    fun requestCapture() {
        val state = captureState.value
        if (state == CaptureState.Prepared || state == CaptureState.Success || state == CaptureState.Failed) {
            _captureRequests.tryEmit(Unit)
        }
    }

    fun clear() {
        permissionResultData = null
        captureState.value = CaptureState.Idle
    }
}
