package com.doohtracker.box.data.hardware

import kotlinx.coroutines.flow.Flow

interface HdmiSignalMonitor {
    val isSignalActive: Flow<Boolean>
}
