package com.doohtracker.box.data.hardware

import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.data.repository.CampaignRepository
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class HeartbeatServer @Inject constructor(
    private val signalMonitor: SignalMonitor,
    private val campaignRepository: CampaignRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var isRunning = false

    fun start(port: Int = 50005) {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                socket = DatagramSocket(port).apply {
                    reuseAddress = true
                    broadcast = true
                }
                Log.i("HeartbeatServer", "Listening for UDP heartbeats on port $port")

                val buffer = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    withContext(Dispatchers.IO) {
                        socket?.receive(packet)
                    }

                    val message = String(packet.data, 0, packet.length)
                    // Format: PULSE:{status}:{hostname}:{ports}:{timestamp}:{transmission}
                    Log.d("HeartbeatServer", "Received heartbeat from ${packet.address.hostAddress}: $message")
                    
                    val parts = message.split(":")
                    val metadata = if (parts.size >= 6 && parts[0] == "PULSE") {
                        SourceMetadata(
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = parts[1],
                            hostname = parts[2],
                            activePorts = parts[3],
                            isTransmitting = parts[5].trim().uppercase() == "YES",
                            lastSeen = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                        )
                    } else if (parts.size >= 5 && parts[0] == "PULSE") {
                        SourceMetadata(
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = parts[1],
                            hostname = parts[2],
                            activePorts = parts[3],
                            isTransmitting = false,
                            lastSeen = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                        )
                    } else if (parts.size >= 4 && parts[0] == "PULSE") {
                        SourceMetadata(
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = parts[1],
                            hostname = parts[2],
                            activePorts = parts[3],
                            isTransmitting = false,
                            lastSeen = System.currentTimeMillis()
                        )
                    } else if (parts.size >= 3 && parts[0] == "PULSE") {
                        SourceMetadata(
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = parts[1],
                            hostname = parts[2],
                            activePorts = "Unknown",
                            isTransmitting = false,
                            lastSeen = System.currentTimeMillis()
                        )
                    } else {
                        SourceMetadata(
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = "ACTIVE",
                            hostname = "Generic Source",
                            activePorts = "None",
                            isTransmitting = false,
                            lastSeen = System.currentTimeMillis()
                        )
                    }

                    // Report to the monitor that we have an active network signal with info
                    signalMonitor.reportHeartbeat(metadata)
                }
            } catch (e: Exception) {
                Log.e("HeartbeatServer", "Server error", e)
            } finally {
                stop()
            }
        }

        // Discovery Broadcast: Let the Windows/Linux/Mac app find us
        scope.launch {
            while (isActive) {
                try {
                    val siteId = campaignRepository.getActivationCode().ifBlank { "UNACTIVATED" }
                    val discoveryMsg = "DOOH_TRACKER_ADVERT:${port}:${siteId}".toByteArray()
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val discoveryPacket = DatagramPacket(discoveryMsg, discoveryMsg.size, broadcastAddr, 50006)
                    socket?.send(discoveryPacket)
                } catch (e: Exception) {
                    // Ignore broadcast errors
                }
                delay(5000.milliseconds) // Broadcast every 5 seconds
            }
        }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
    }
}
