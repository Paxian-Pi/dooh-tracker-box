package com.doohtracker.box.data.hardware

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbConnectionMonitor: UsbConnectionMonitor
) : SignalMonitor {

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSourceActive = MutableStateFlow(false)
    override val isSourceActive: StateFlow<Boolean> = _isSourceActive.asStateFlow()

    private val _sourceMetadata = MutableStateFlow<SourceMetadata?>(null)
    override val sourceMetadata: StateFlow<SourceMetadata?> = _sourceMetadata.asStateFlow()

    private val _isCameraAvailable = MutableStateFlow(false)
    
    private var lastHeartbeatTime = 0L

    override fun reportHeartbeat(metadata: SourceMetadata?) {
        lastHeartbeatTime = System.currentTimeMillis()
        if (metadata != null) {
            _sourceMetadata.value = metadata
        }
        if (!_isSourceActive.value) {
            _isSourceActive.value = true
        }
    }

    init {
        // Monitor for OS-level camera availability
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                _isCameraAvailable.value = true
            }

            override fun onCameraUnavailable(cameraId: String) {
                // If it's unavailable because WE are using it, don't set to false
            }
        }, Handler(Looper.getMainLooper()))

        // Monitor for heartbeat timeouts
        monitorScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastHeartbeatTime > 5000) { // Increased tolerance to 5s
                    if (_isSourceActive.value) {
                        _isSourceActive.value = false
                    }
                }
                delay(1000)
            }
        }
    }

    // Directly use the specialized UsbConnectionMonitor state
    override val isUsbConnected: Flow<Boolean> = usbConnectionMonitor.isUsbDeviceConnected

    // A signal is "Technically Active" if the USB is connected and the Camera is ready in the OS
    val isTechnicallyActive: Flow<Boolean> = combine(isUsbConnected, _isCameraAvailable) { usb, cam ->
        usb && cam
    }
}
