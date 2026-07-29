package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow

interface SignalMonitor {
    val isUsbConnected: Flow<Boolean>
    val isSourceActive: Flow<Boolean>
    val sourceMetadata: Flow<SourceMetadata?>
    fun reportHeartbeat(metadata: SourceMetadata? = null)
}

data class SourceMetadata(
    val ipAddress: String = "Unknown",
    val hostname: String = "Generic Source",
    val status: String = "ACTIVE",
    val activePorts: String = "None",
    val isTransmitting: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
