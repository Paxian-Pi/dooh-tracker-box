package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow

interface UsbConnectionMonitor {
    val isUsbDeviceConnected: Flow<Boolean>
    val onNewPermissionGranted: Flow<Unit>
    fun triggerCheck()
    fun suppressAutomatedRequests(durationMillis: Long)
}
