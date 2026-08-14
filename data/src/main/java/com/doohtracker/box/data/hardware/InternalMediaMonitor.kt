package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow

interface InternalMediaMonitor {
    val isPermissionGranted: Flow<Boolean>
    fun checkPermission()
    
    /**
     * Sends a loopback UDP pulse to the HeartbeatServer representing the local Android player state.
     */
    suspend fun sendLocalPulse(
        status: String,
        isTransmitting: Boolean,
        mediaTitle: String,
        siteId: String
    )
}
