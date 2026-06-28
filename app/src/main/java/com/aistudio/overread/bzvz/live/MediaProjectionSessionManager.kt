package com.aistudio.overread.bzvz.live

import android.content.Intent

/**
 * R2 Skeleton for MediaProjectionSessionManager.
 * Does not implement VirtualDisplay or capture logic yet.
 * Only holds token (resultData) in memory and manages basic state.
 */
object MediaProjectionSessionManager {
    var resultCode: Int = 0
        private set
        
    var resultData: Intent? = null
        private set

    val isPrepared: Boolean
        get() = resultData != null

    fun prepare(code: Int, data: Intent) {
        resultCode = code
        resultData = data
    }

    fun getSessionData(): Pair<Int, Intent>? {
        if (resultData != null) {
            return Pair(resultCode, resultData!!)
        }
        return null
    }

    fun clear() {
        resultCode = 0
        resultData = null
    }
}
