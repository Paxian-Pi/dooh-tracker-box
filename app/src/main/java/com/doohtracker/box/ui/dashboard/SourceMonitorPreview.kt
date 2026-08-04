package com.doohtracker.box.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SourceMonitorPreview(
    modifier: Modifier = Modifier,
    signalMonitor: SignalMonitor
) {
    val metadata by signalMonitor.sourceMetadata.collectAsState(initial = null)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E2136)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (metadata != null) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .then(Modifier.verticalScroll(scrollState)),
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
                    Text("Current File: ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        metadata!!.mediaTitle, 
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
