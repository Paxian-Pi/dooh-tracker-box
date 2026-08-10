package com.doohtracker.box.data.hardware

import com.doohtracker.box.data.repository.CampaignRepository
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
                    val parts = message.split(":")
                    
                    if (parts.size >= 8 && parts[0] == "PULSE") {
                        // Protocol: PULSE:STATUS:HOSTNAME:PORTS:TIMESTAMP:TRANSMITTING:MEDIA_TITLE:SITE_ID
                        // Site ID is ALWAYS the last part.
                        val siteId = parts.last()
                        
                        // Media title is everything between index 6 and the last part
                        val mediaTitle = parts.subList(6, parts.size - 1).joinToString(":")

                        val metadata = SourceMetadata(
                            siteId = siteId,
                            ipAddress = packet.address.hostAddress ?: "Unknown",
                            status = parts[1],
                            hostname = parts[2],
                            activePorts = parts[3],
                            isTransmitting = parts[5].trim().uppercase() == "YES",
                            mediaTitle = mediaTitle,
                            lastSeen = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                        )
                        Log.d("HeartbeatServer", "Routing heartbeat for Site ID: $siteId")
                        signalMonitor.reportHeartbeat(siteId, metadata)
                    }
                }
            } catch (e: Exception) {
                Log.e("HeartbeatServer", "Server error", e)
            } finally {
                stop()
            }
        }

        // Discovery Broadcast: Advertise ALL active Site IDs and their current owners
        scope.launch {
            while (isActive) {
                try {
                    val siteIds = campaignRepository.getActivationCodes()
                    val allMetadata = signalMonitor.allSourceMetadata.first()
                    
                    val siteStatusList = siteIds.map { sid ->
                        val owner = allMetadata[sid]?.hostname ?: "NONE"
                        "$sid|$owner"
                    }
                    
                    val siteStatusCsv = if (siteStatusList.isEmpty()) "UNACTIVATED|NONE" else siteStatusList.joinToString(",")
                    
                    val discoveryMsg = "DOOH_TRACKER_ADVERT:${port}:${siteStatusCsv}".toByteArray()
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val discoveryPacket = DatagramPacket(discoveryMsg, discoveryMsg.size, broadcastAddr, 50006)
                    socket?.send(discoveryPacket)
                } catch (e: Exception) {
                }
                delay(2000.milliseconds)
            }
        }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
    }
}
