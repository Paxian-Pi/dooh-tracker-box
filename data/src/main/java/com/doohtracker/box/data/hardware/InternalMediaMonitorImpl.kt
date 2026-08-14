package com.doohtracker.box.data.hardware

import android.content.Context
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalMediaMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InternalMediaMonitor {

    private val _isPermissionGranted = MutableStateFlow(false)
    override val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    init {
        checkPermission()
    }

    override fun checkPermission() {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isGranted = enabledListeners?.contains(context.packageName) == true
        _isPermissionGranted.value = isGranted
    }

    override suspend fun sendLocalPulse(
        status: String,
        isTransmitting: Boolean,
        mediaTitle: String,
        siteId: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                val hostName = "Local-Android-System"
                val ports = "0"
                val timestamp = System.currentTimeMillis()
                val transmitting = if (isTransmitting) "YES" else "NO"
                
                // Protocol: PULSE:STATUS:HOSTNAME:PORTS:TIMESTAMP:TRANSMITTING:MEDIA_TITLE:SITE_ID
                val message = "PULSE:$status:$hostName:$ports:$timestamp:$transmitting:$mediaTitle:$siteId"
                val buffer = message.toByteArray()
                
                val address = InetAddress.getByName("127.0.0.1")
                val packet = DatagramPacket(buffer, buffer.size, address, 50005)
                
                socket.send(packet)
                socket.close()
                Log.v("InternalMonitor", "Sent local loopback pulse: $mediaTitle for $siteId")
            } catch (e: Exception) {
                Log.e("InternalMonitor", "Failed to send local pulse", e)
            }
        }
    }
}
