package com.doohtracker.box.data.system

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

data class SystemHealth(
    val ramUsage: String,
    val storageUsage: String,
    val uptime: String,
    val ipAddress: String
)

interface SystemHealthMonitor {
    val healthInfo: Flow<SystemHealth>
}

@Singleton
class SystemHealthMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemHealthMonitor {

    override val healthInfo: Flow<SystemHealth> = flow {
        while (true) {
            emit(getHealthSnapshot())
            delay(30.seconds)
        }
    }

    private fun getHealthSnapshot(): SystemHealth {
        return SystemHealth(
            ramUsage = getRamUsage(),
            storageUsage = getStorageUsage(),
            uptime = getUptime(),
            ipAddress = getIpAddress()
        )
    }

    private fun getRamUsage(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val total = memoryInfo.totalMem / (1024 * 1024)
        val available = memoryInfo.availMem / (1024 * 1024)
        val used = total - available
        
        return "$used / $total MB"
    }

    private fun getStorageUsage(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val totalGB = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
        val availableGB = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        val usedGB = totalGB - availableGB
        
        val percentage = if (totalBlocks > 0) {
            ((totalBlocks - availableBlocks) * 100) / totalBlocks
        } else 0
        
        return "$usedGB / $totalGB GB ~ $percentage%"
    }

    private fun getUptime(): String {
        val millis = SystemClock.elapsedRealtime()
        val days = millis / (24 * 60 * 60 * 1000)
        val hours = (millis / (60 * 60 * 1000)) % 24
        val minutes = (millis / (60 * 1000)) % 60
        
        return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "N/A"
    }
}
