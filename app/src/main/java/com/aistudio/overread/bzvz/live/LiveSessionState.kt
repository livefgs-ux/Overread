package com.aistudio.overread.bzvz.live

enum class LiveSessionState {
    Idle,
    PermissionRequired,
    PermissionGranted,
    StartingService,
    CreatingProjection,
    RegisteringCallback,
    CreatingImageReader,
    CreatingVirtualDisplay,
    Active,
    Paused,
    Stopping,
    Stopped,
    Failed
}
