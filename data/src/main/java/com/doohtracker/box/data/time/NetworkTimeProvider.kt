package com.doohtracker.box.data.time

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class NetworkTimeProvider @Inject constructor() : TimeProvider {
    private var offset: Long = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        syncWithNtp()
    }

    override fun getCurrentTimeMillis(): Long {
        return System.currentTimeMillis() + offset
    }

    private fun syncWithNtp() {
        scope.launch {
            while (isActive) {
                try {
                    val ntpTime = getNtpTime()
                    if (ntpTime > 0) {
                        offset = ntpTime - System.currentTimeMillis()
                        Log.d("TimeProvider", "NTP Sync Success. Offset: $offset ms")
                    }
                } catch (e: Exception) {
                    Log.e("TimeProvider", "NTP Sync Failed: ${e.message}")
                }
                // Sync every hour
                delay(1.hours)
            }
        }
    }

    private fun getNtpTime(): Long {
        val host = "time.google.com"
        return try {
            val address = InetAddress.getByName(host)
            val ntpData = ByteArray(48)
            ntpData[0] = 0x1B // NTP mode 3 (client) and version 3

            val socket = DatagramSocket().apply {
                soTimeout = 5000
            }
            val packet = DatagramPacket(ntpData, ntpData.size, address, 123)
            socket.send(packet)

            val responsePacket = DatagramPacket(ntpData, ntpData.size)
            socket.receive(responsePacket)
            socket.close()

            // Extract seconds from byte 40 to 43
            val seconds = (ntpData[40].toLong() and 0xFF shl 24) or
                          (ntpData[41].toLong() and 0xFF shl 16) or
                          (ntpData[42].toLong() and 0xFF shl 8) or
                          (ntpData[43].toLong() and 0xFF)
            
            // NTP epoch is 1900, Unix epoch is 1970. Difference is 2208988800 seconds.
            (seconds - 2208988800L) * 1000
        } catch (_: Exception) {
            0L
        }
    }
}
