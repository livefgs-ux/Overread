package com.example.capture

enum class CaptureState {
    Idle,
    PermissionRequired,
    Prepared,
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
