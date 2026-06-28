package com.aistudio.overread.bzvz.live

class FrameSampler(val intervalMs: Long = 1000L) {
    private var lastAcceptedTimestamp: Long = 0L

    fun shouldSample(timestampMs: Long, isPaused: Boolean): Boolean {
        if (isPaused) {
            return false
        }
        
        if (timestampMs - lastAcceptedTimestamp >= intervalMs) {
            lastAcceptedTimestamp = timestampMs
            return true
        }

        return false
    }

    fun clear() {
        lastAcceptedTimestamp = 0L
    }
}
