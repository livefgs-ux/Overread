package com.aistudio.overread.bzvz.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LiveModeController {
    private val _state = MutableStateFlow(LiveSessionState.Idle)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val _framesReceived = MutableStateFlow(0)
    val framesReceived: StateFlow<Int> = _framesReceived.asStateFlow()

    private val _framesAccepted = MutableStateFlow(0)
    val framesAccepted: StateFlow<Int> = _framesAccepted.asStateFlow()

    private val _framesDropped = MutableStateFlow(0)
    val framesDropped: StateFlow<Int> = _framesDropped.asStateFlow()

    private val _samplerIntervalMs = MutableStateFlow(1000L)
    val samplerIntervalMs: StateFlow<Long> = _samplerIntervalMs.asStateFlow()

    private val _stabilityState = MutableStateFlow(StabilityState.Unknown)
    val stabilityState: StateFlow<StabilityState> = _stabilityState.asStateFlow()

    fun setStabilityState(newState: StabilityState) {
        _stabilityState.value = newState
    }

    fun setState(newState: LiveSessionState) {
        _state.value = newState
    }

    fun incrementFrames() {
        _framesReceived.value += 1
    }

    fun incrementFramesAccepted() {
        _framesAccepted.value += 1
    }

    fun incrementFramesDropped() {
        _framesDropped.value += 1
    }

    fun startIntent() {
        if (_state.value == LiveSessionState.Idle || _state.value == LiveSessionState.Stopped || _state.value == LiveSessionState.Failed) {
            _state.value = LiveSessionState.PermissionRequired
            _framesReceived.value = 0
            _framesAccepted.value = 0
            _framesDropped.value = 0
            _stabilityState.value = StabilityState.Unknown
        }
    }

    fun onPermissionGranted() {
        if (_state.value == LiveSessionState.PermissionRequired) {
            _state.value = LiveSessionState.PermissionGranted
        }
    }
    
    fun onPermissionDenied() {
        _state.value = LiveSessionState.Failed
    }

    fun pause() {
        if (_state.value == LiveSessionState.Active) {
            _state.value = LiveSessionState.Paused
        }
    }

    fun resume() {
        if (_state.value == LiveSessionState.Paused) {
            _state.value = LiveSessionState.Active
        }
    }

    fun stop() {
        _state.value = LiveSessionState.Stopping
    }

    fun clear() {
        MediaProjectionSessionManager.clear()
        _state.value = LiveSessionState.Idle
        _framesReceived.value = 0
        _framesAccepted.value = 0
        _framesDropped.value = 0
        _stabilityState.value = StabilityState.Unknown
    }
}
