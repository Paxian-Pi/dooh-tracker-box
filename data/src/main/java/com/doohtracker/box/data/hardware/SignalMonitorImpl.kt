package com.doohtracker.box.data.hardware

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class SignalMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbConnectionMonitor: UsbConnectionMonitor
) : SignalMonitor {

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSourceActive = MutableStateFlow(false)
    override val isSourceActive: StateFlow<Boolean> = _isSourceActive.asStateFlow()

    private val _allSourceMetadata = MutableStateFlow<Map<String, SourceMetadata>>(emptyMap())
    override val allSourceMetadata: StateFlow<Map<String, SourceMetadata>> = _allSourceMetadata.asStateFlow()

    private val _isCameraAvailable = MutableStateFlow(false)
    
    private val heartbeatTimestamps = mutableMapOf<String, Long>()

    override fun reportHeartbeat(siteId: String, metadata: SourceMetadata) {
        val now = System.currentTimeMillis()
        
        // Ownership Lock Per Site ID: only allow heartbeats from the SAME hostname for that Site ID
        val currentMap = _allSourceMetadata.value
        val existingMetadata = currentMap[siteId]
        val lastSeen = heartbeatTimestamps[siteId] ?: 0L
        val isCurrentlyActive = (now - lastSeen) < 10000 // Increased grace period to 10s
        
        if (isCurrentlyActive && existingMetadata != null) {
            if (existingMetadata.hostname != metadata.hostname) {
                // This Site ID is already "Occupied" by another machine
                Log.w("SignalMonitor", "Rejected heartbeat for $siteId from ${metadata.hostname}. Owned by ${existingMetadata.hostname}")
                return
            }
        }

        heartbeatTimestamps[siteId] = now
        val newMap = currentMap.toMutableMap()
        newMap[siteId] = metadata.copy(siteId = siteId, lastSeen = now)
        _allSourceMetadata.value = newMap

        if (!_isSourceActive.value) {
            _isSourceActive.value = true
            Log.d("SignalMonitor", "Signal activated by Site ID: $siteId")
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
            }
        }, Handler(Looper.getMainLooper()))

        // Monitor for heartbeat timeouts across ALL identities
        monitorScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val currentMap = _allSourceMetadata.value
                val newMap = currentMap.toMutableMap()
                var anyActive = false

                val iterator = heartbeatTimestamps.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > 15000) { // Increased to 15s to prevent flickering
                        newMap.remove(entry.key)
                        iterator.remove()
                    } else {
                        anyActive = true
                    }
                }

                if (newMap.size != currentMap.size) {
                    _allSourceMetadata.value = newMap
                }
                
                // Final Inactive Check: Only set to false if the Hub has been silent for 30s
                if (!anyActive && _isSourceActive.value) {
                    val longestSilent = heartbeatTimestamps.values.maxOrNull() ?: 0L
                    if (now - longestSilent > 30000) {
                        _isSourceActive.value = false
                        Log.d("SignalMonitor", "Hub went silent for 30s. Setting inactive.")
                    }
                }
                
                delay(2000.milliseconds)
            }
        }
    }

    override val isUsbConnected: Flow<Boolean> = usbConnectionMonitor.isUsbDeviceConnected

    val isTechnicallyActive: Flow<Boolean> = combine(isUsbConnected, _isCameraAvailable) { usb, cam ->
        usb && cam
    }
}
