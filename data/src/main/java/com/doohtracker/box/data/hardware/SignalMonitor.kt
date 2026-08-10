package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SignalMonitor {
    val isUsbConnected: Flow<Boolean>
    val isSourceActive: StateFlow<Boolean>
    val allSourceMetadata: StateFlow<Map<String, SourceMetadata>>
    fun reportHeartbeat(siteId: String, metadata: SourceMetadata)
}

data class SourceMetadata(
    val siteId: String = "",
    val ipAddress: String = "Unknown",
    val hostname: String = "Generic Source",
    val status: String = "ACTIVE",
    val activePorts: String = "None",
    val isTransmitting: Boolean = false,
    val mediaTitle: String = "Unknown Content",
    val lastSeen: Long = System.currentTimeMillis()
)
