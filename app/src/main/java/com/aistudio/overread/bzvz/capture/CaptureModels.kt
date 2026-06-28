package com.aistudio.overread.bzvz.capture

enum class CaptureStage {
    Idle,
    PermissionRequired,
    PreparingPermission,
    PermissionGranted,
    ReadyForOneCapture,
    StartingService,
    CreatingMediaProjection,
    RegisteringCallback,
    CreatingImageReader,
    CreatingVirtualDisplay,
    WaitingForFrame,
    ImageAvailable,
    AcquiringImage,
    ConvertingBitmap,
    BitmapReady,
    SendingToOcr,
    OcrProcessing,
    CaptureCompleted,
    CaptureFailed,
    CleanupStarted,
    CleanupCompleted
}

enum class CaptureState {
    Idle,
    PermissionRequired,
    ReadyForOneCapture,
    Capturing,
    Success,
    Failed
}

data class CaptureResult(
    val success: Boolean,
    val width: Int? = null,
    val height: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val isSecureOrEmpty: Boolean = false
)
