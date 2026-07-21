package com.doohtracker.box.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.doohtracker.box.ui.theme.StatusCyan
import com.doohtracker.box.ui.theme.StatusGreen
import com.doohtracker.box.ui.theme.StatusRed
import com.doohtracker.box.ui.theme.StatusYellow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val isHdmiActive by viewModel.isHdmiActive.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val currentCampaign by viewModel.currentCampaignName.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    val campaignCount by viewModel.totalCampaignsCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val location by viewModel.currentLocation.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val anyCampaignActive by viewModel.anyCampaignActive.collectAsState()
    val systemHealth by viewModel.systemHealth.collectAsState()

    val restartButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Left Column: Status Grid
            Column(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "DOOH Tracker Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                // Row 1: Core Connectivity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "HDMI Signal",
                        status = if (isHdmiActive) "Active" else "Inactive",
                        color = if (isHdmiActive) StatusGreen else StatusRed,
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "Internet",
                        status = if (isConnected) "Online" else "Offline",
                        color = if (isConnected) StatusGreen else StatusRed,
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "Internal IP",
                        status = systemHealth?.ipAddress ?: "Checking...",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Campaign Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "Current Campaign",
                        status = currentCampaign,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(2f),
                        isScrolling = true
                    )

                    StatusCard(
                        title = "Downloaded",
                        status = "$campaignCount ($syncStatus)",
                        color = if (campaignCount > 0) StatusCyan else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Location and Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "Device Location",
                        status = location,
                        color = if (location.contains("Disabled")) StatusRed else StatusYellow,
                        modifier = Modifier.weight(2f),
                        onClick = { viewModel.openLocationSettings() }
                    )

                    if (!anyCampaignActive) {
                        StatusCard(
                            title = "Last Sync",
                            status = lastSyncTime,
                            color = StatusCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Row 4: System Health
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusCard(
                        title = "RAM",
                        status = systemHealth?.ramUsage ?: "Checking...",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "Storage",
                        status = systemHealth?.storageUsage ?: "Checking...",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    StatusCard(
                        title = "Uptime",
                        status = systemHealth?.uptime ?: "Checking...",
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Right Column: Logs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "Audit Trail",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Surface(
                    onClick = {},
                    modifier = Modifier.fillMaxSize(),
                    shape = ClickableSurfaceDefaults.shape(RectangleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF1E2136),
                        contentColor = Color.White
                    )
                ) {
                    LogsList(logs)
                }
            }
        }

        // Custom Restart Dialog Overlay
        if (showRestartDialog) {
            LaunchedEffect(Unit) {
                restartButtonFocusRequester.requestFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Column(
                        modifier = Modifier.padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = "Restart Required",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "A new USB Video device has been authorized. Please restart the device to apply changes correctly.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.width(400.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.reboot() },
                                modifier = Modifier.focusRequester(restartButtonFocusRequester)
                            ) {
                                Text("Restart Now")
                            }
                            Button(
                                onClick = { viewModel.dismissRestartDialog() }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatusCard(
    title: String, 
    status: String, 
    color: Color, 
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E2136),
            contentColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (isScrolling) {
                AutoScrollingText(
                    text = status,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AutoScrollingText(
    text: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle
) {
    val scrollState = rememberScrollState()
    
    // Simple marquee effect
    LaunchedEffect(text) {
        while (true) {
            delay(2000.milliseconds)
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(
                    durationMillis = (scrollState.maxValue * 15).coerceAtLeast(1000),
                    easing = LinearEasing
                )
            )
            delay(2000.milliseconds)
            scrollState.scrollTo(0)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, enabled = false)
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LogsList(logs: List<TrackingLogUiModel>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No logs yet", modifier = Modifier.alpha(0.5f), color = Color.White)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.2f)),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Campaign", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), color = Color.Gray)
                Text("Date", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), color = Color.Gray)
                Text("Time & Sync", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color.Gray)
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LogItem(log: TrackingLogUiModel) {
    val dateSdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val date = dateSdf.format(Date(log.timestamp))
    val time = timeSdf.format(Date(log.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Campaign Title
        Text(
            text = log.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = Color.White
        )
        
        // Column 2: Date
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f),
            color = Color.LightGray
        )
        
        // Column 3: Time & Synced
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (log.isSynced) "✓" else "☁",
                color = if (log.isSynced) StatusGreen else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
