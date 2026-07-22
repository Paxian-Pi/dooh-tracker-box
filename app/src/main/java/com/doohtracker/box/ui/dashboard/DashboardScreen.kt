package com.doohtracker.box.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.doohtracker.box.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
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
    val currentMediaCampaign by viewModel.playbackManager.currentCampaign.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }

    val restartButtonFocusRequester = remember { FocusRequester() }
    val exportButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if(!anyCampaignActive) {
                        val spots by viewModel.dailySpotsSummary.collectAsState()
                        StatusCard(
                            title = "Total Spots Within Campaign Window",
                            info = "Export to PDF/CSV",
                            status = spots,
                            color = StatusCyan,
                            modifier = Modifier.weight(1.5f),
                            isScrolling = true,
                            onClick = { showExportDialog = true }
                        )
                    }
                }

                // Row 3: Location and Sync/Spots
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
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.openDateTimeSettings() }
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

            // Right Column: Player and Logs
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 16.dp)
                        .background(Color.Black, shape = RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    val playbackManager = viewModel.playbackManager
                    val campaign = currentMediaCampaign
                    
                    val mediaType = when {
                        campaign == null -> "none"
                        campaign.mediaType?.lowercase() == "image" || 
                        campaign.mediaUrl?.lowercase()?.let {
                            it.endsWith(".jpg") || it.endsWith(".jpeg") || 
                            it.endsWith(".png") || it.endsWith(".webp") 
                        } == true -> "image"
                        else -> "video"
                    }

                    Crossfade(
                        targetState = mediaType,
                        animationSpec = tween(1000),
                        label = "TypeTransition"
                    ) { type ->
                        when (type) {
                            "image" -> {
                                // Use a nested crossfade for image-to-image transitions
                                Crossfade(targetState = campaign, label = "ImageTransition") { imgCampaign ->
                                    val imageModel = imgCampaign?.localPath ?: imgCampaign?.mediaUrl
                                    AsyncImage(
                                        model = imageModel,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                            "video" -> {
                                // For videos, we keep the SAME AndroidView to avoid flashes
                                AndroidView(
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            player = playbackManager.player
                                            useController = false
                                            @Suppress("DEPRECATION")
                                            useArtwork = false
                                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                            try {
                                                val method = javaClass.getMethod("setSurfaceType", Int::class.javaPrimitiveType)
                                                method.invoke(this, 2) // SURFACE_TYPE_TEXTURE_VIEW
                                            } catch (e: Exception) {}
                                            setKeepContentOnPlayerReset(true)
                                            keepScreenOn = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                                    update = { view ->
                                        if (view.player != playbackManager.player) {
                                            view.player = playbackManager.player
                                        }
                                    },
                                    onRelease = { view ->
                                        view.player = null
                                    }
                                )
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val text = if (anyCampaignActive) "Downloading campaigns..." else "No Active Content"
                                    Text(text, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Audit Trail",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    onClick = {},
                    modifier = Modifier.weight(1f),
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
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.wrapContentSize().padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).widthIn(max = 450.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Restart Required",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "A new USB Video device has been authorized. Please restart the device to apply changes correctly.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.reboot() },
                                modifier = Modifier
                                    .focusRequester(restartButtonFocusRequester)
                                    .clickable { viewModel.reboot() }
                            ) {
                                Text("Restart Now")
                            }
                            Button(
                                onClick = { viewModel.dismissRestartDialog() },
                                modifier = Modifier.clickable { viewModel.dismissRestartDialog() }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        // Custom Export Dialog Overlay
        if (showExportDialog) {
            LaunchedEffect(Unit) {
                exportButtonFocusRequester.requestFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.wrapContentSize().padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).widthIn(max = 450.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Export Report",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Select format for the daily spots report:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.exportCampaignData("csv")
                                    showExportDialog = false 
                                },
                                modifier = Modifier
                                    .focusRequester(exportButtonFocusRequester)
                                    .clickable { 
                                        viewModel.exportCampaignData("csv")
                                        showExportDialog = false 
                                    }
                            ) {
                                Text("CSV")
                            }
                            Button(
                                onClick = { 
                                    viewModel.exportCampaignData("pdf")
                                    showExportDialog = false 
                                },
                                modifier = Modifier.clickable {
                                    viewModel.exportCampaignData("pdf")
                                    showExportDialog = false
                                }
                            ) {
                                Text("PDF")
                            }
                            Button(
                                onClick = { showExportDialog = false },
                                modifier = Modifier.clickable { showExportDialog = false }
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
    modifier: Modifier = Modifier,
    title: String,
    info: String? = null,
    status: String,
    color: Color,
    isScrolling: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = true,
                    onClick = onClick
                )
            } else Modifier
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E2136),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                if (info != null) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

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
                Text(
                    "Campaign",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1.2f),
                    color = Color.Gray
                )
                Text("Date", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), color = Color.Gray)
                Text(
                    "Time & Sync",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = Color.Gray
                )
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
