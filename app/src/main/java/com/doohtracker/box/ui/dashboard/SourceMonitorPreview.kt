package com.doohtracker.box.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.ui.theme.StatusCyan
import com.doohtracker.box.ui.theme.StatusGreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SourceMonitorPreview(
    modifier: Modifier = Modifier,
    signalMonitor: SignalMonitor
) {
    val metadata by signalMonitor.sourceMetadata.collectAsState(initial = null)
    
    // UI state for smoothed transmission status
    var displayedIsTransmitting by remember { mutableStateOf(false) }

    // Effect to apply 1-second delay to status changes for smoother UX
    LaunchedEffect(metadata?.isTransmitting) {
        val targetValue = metadata?.isTransmitting ?: false

        if (metadata!!.activePorts == "Internal/None") {
            displayedIsTransmitting = false
            return@LaunchedEffect
        }
        if (targetValue != displayedIsTransmitting) {
            delay(1000.milliseconds) // 1 second transition delay
            displayedIsTransmitting = targetValue
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E2136)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (metadata != null) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Source Monitor Info",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusGreen,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Machine: ", color = Color.Gray, fontSize = 12.sp)
                    Text(metadata!!.hostname, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("IP Address: ", color = Color.Gray, fontSize = 12.sp)
                    Text(metadata!!.ipAddress, color = StatusCyan, fontSize = 13.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ", color = Color.Gray, fontSize = 12.sp)
                    Text(metadata!!.status, color = if(metadata!!.status == "HEALTHY") StatusGreen else Color.Yellow, fontSize = 13.sp)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Outputs: ", color = Color.Gray, fontSize = 12.sp)
                    Text(metadata!!.activePorts, color = Color.White, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Media Stream: ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        if(displayedIsTransmitting) "TRANSMITTING" else "IDLE", 
                        color = if(displayedIsTransmitting) StatusGreen else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Source Pulse: ", color = Color.Gray, fontSize = 11.sp)
                    Text(sdf.format(Date(metadata!!.lastSeen)), color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Waiting for Network Pulse...",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
