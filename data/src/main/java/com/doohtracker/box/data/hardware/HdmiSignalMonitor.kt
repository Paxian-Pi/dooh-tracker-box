package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow

interface HdmiSignalMonitor {
    val isUsbConnected: Flow<Boolean>
    val isSourceActive: Flow<Boolean>
    fun reportHeartbeat()
}
