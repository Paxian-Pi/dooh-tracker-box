package com.doohtracker.box.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.size
import coil.compose.AsyncImage
import com.doohtracker.box.ui.theme.StatusCyan
import com.doohtracker.box.ui.theme.StatusGreen
import com.doohtracker.box.ui.theme.StatusRed
import com.doohtracker.box.ui.theme.StatusYellow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val isSourceLinked by viewModel.isSourceLinked.collectAsState()
    val sourceMonitorStatus by viewModel.sourceMonitorStatus.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val currentCampaign by viewModel.currentCampaignName.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    val campaignCount by viewModel.totalCampaignsCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val location by viewModel.currentLocation.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val localIp by viewModel.localIpAddress.collectAsState()
    val anyCampaignActive by viewModel.anyCampaignActive.collectAsState()
    val systemHealth by viewModel.systemHealth.collectAsState()
    val currentMediaCampaign by viewModel.playbackManager.currentCampaign.collectAsState()
    val isHdmiStreamEnabled by viewModel.isHdmiStreamEnabled.collectAsState()
    val siteId by viewModel.activationCode.collectAsState()
    val showPortDialog by viewModel.showPortSelectionDialog.collectAsState()
    val detectedPorts by viewModel.detectedPorts.collectAsState()
    val authorizedPort by viewModel.authorizedPort.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val restartButtonFocusRequester = remember { FocusRequester() }
    val logoutButtonFocusRequester = remember { FocusRequester() }
    val exportButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DOOH Tracker Dashboard",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Source Preview",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isHdmiStreamEnabled && sourceMonitorStatus != "Inactive") MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(
                                checked = isHdmiStreamEnabled && sourceMonitorStatus != "Inactive",
                                onCheckedChange = { viewModel.toggleHdmiStream(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }

                    // Row 1: IP Address
                    Text(
                        text = "IP Address: $localIp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Row 2: Core Connectivity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusCard(
                            title = if (isSourceLinked) "Source Monitor" else "Tracker Status",
                            status = sourceMonitorStatus,
                            color = when (sourceMonitorStatus) {
                                "Active" -> StatusGreen
                                "Ready" -> StatusCyan
                                "Window Closed" -> StatusYellow
                                "No Media Detected" -> Color(0xFF87CEEB) // Light Blue (Sky Blue)
                                else -> StatusRed
                            },
                            modifier = Modifier.weight(1f)
                        )

                        StatusCard(
                            title = "Internet",
                            status = if (isConnected) "Online" else "Offline",
                            color = if (isConnected) StatusGreen else StatusRed,
                            modifier = Modifier.weight(1f)
                        )

                        StatusCard(
                            title = "Site ID",
//                            info = "IP: $localIp",
                            status = siteId,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: Campaign Status
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
                        if (!anyCampaignActive) {
                            val spots by viewModel.dailySpotsSummary.collectAsState()
                            StatusCard(
                                title = "Total Spots Within Campaign Window",
                                info = "Export to PDF/CSV",
                                status = spots,
                                color = StatusCyan,
                                modifier = Modifier.weight(1.5f),
                                isScrolling = true,
                                onClick = {
                                    if (spots == "No spots today") {
                                        Toast.makeText(context, "No spots to export today", Toast.LENGTH_LONG).show()
                                    } else showExportDialog = true
                                }
                            )
                        }
                    }

                    // Row 4: Location and Sync/Spots
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

                    // Row 5: System Health
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

                    if (!anyCampaignActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvButton(
                                onClick = { showLogoutDialog = true },
                                colors = TvButtonDefaults.colors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .scale(0.9f)
                                    .clickable { showLogoutDialog = true }
                                    .background(Color.Black, shape = RoundedCornerShape(10.dp))
                            ) {
                                Text(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), text = "Re-activate Device")
                            }
                        }
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
                            isSourceLinked && isHdmiStreamEnabled -> "hdmi"
                            campaign == null -> "none"
                            campaign.mediaType?.lowercase() == "image" ||
                                    campaign.mediaUrl?.lowercase()?.let {
                                        it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                                                it.endsWith(".png") || it.endsWith(".webp")
                                    } == true -> "image"

                            else -> "video"
                        }

                        when (mediaType) {
                            "hdmi" -> {
                                SourceMonitorPreview(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1E2136)),
                                    signalMonitor = viewModel.signalMonitor,
                                    authorizedPort = authorizedPort,
                                    onTriggerPortSelection = { viewModel.triggerPortSelection() }
                                )
                            }

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
                                            } catch (e: Exception) {
                                            }
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
                                    val text = if (anyCampaignActive) {
                                        when (sourceMonitorStatus) {
                                            "Ready" -> "Waiting for Source Monitor Link..."
                                            "No Media Detected" -> "Waiting for valid media transmission..."
                                            "Window Closed" -> "No scheduled campaigns in this window"
                                            else -> "Preparing campaigns..."
                                        }
                                    } else {
                                        "No Active Content"
                                    }
                                    Text(
                                        text = text,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
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
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showLogoutDialog = true },
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
        }

        // Custom Logout Dialog Overlay
        if (showLogoutDialog) {
            LaunchedEffect(Unit) {
                logoutButtonFocusRequester.requestFocus()
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
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .widthIn(max = 450.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Re-activate Device?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "This will wipe all device credentials, delete downloaded campaigns, and clear all logs. You will need to reactivate the device.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            TvButton(
                                onClick = {
                                    viewModel.logout()
                                    showLogoutDialog = false
                                },
                                colors = TvButtonDefaults.colors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .focusRequester(logoutButtonFocusRequester)
                                    .clickable {
                                        viewModel.logout()
                                        showLogoutDialog = false
                                    }
                            ) {
                                Text("Logout & Wipe")
                            }
                            TvButton(
                                onClick = { showLogoutDialog = false },
                                modifier = Modifier.clickable { showLogoutDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
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
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .widthIn(max = 450.dp),
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
                            TvButton(
                                onClick = { viewModel.reboot() },
                                modifier = Modifier
                                    .focusRequester(restartButtonFocusRequester)
                                    .clickable { viewModel.reboot() },
                                colors = TvButtonDefaults.colors()
                            ) {
                                Text("Restart Now")
                            }
                            TvButton(
                                onClick = { viewModel.dismissRestartDialog() },
                                modifier = Modifier.clickable { viewModel.dismissRestartDialog() },
                                colors = TvButtonDefaults.colors()
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        // Port Selection Dialog Overlay
        if (showPortDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { /* Block clicks to background */ },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF1E2136)),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .widthIn(max = 400.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown, 
                            contentDescription = null,
                            tint = StatusCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        
                        Text(
                            text = "Select Output Port",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Choose the physical port connected to your DOOH screen:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = Color.LightGray
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Scrollable list of ports for mobile friendliness
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .heightIn(max = 250.dp)
                        ) {
                            detectedPorts.distinct().forEach { port ->
                                androidx.compose.material3.Button(
                                    onClick = { viewModel.authorizePort(port) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (authorizedPort == port) StatusCyan else Color.Black.copy(alpha = 0.3f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = port, fontWeight = FontWeight.Medium)
                                        if (authorizedPort == port) {
                                            Text("Selected", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        androidx.compose.material3.Button(
                            onClick = { viewModel.dismissPortDialog() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Gray),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Custom Export Dialog Overlay
        if (showExportDialog) {
            val allCampaigns by viewModel.allCampaignsList.collectAsState()
            var searchQuery by remember { mutableStateOf("") }
            var selectedCampaignId by remember { mutableStateOf<String?>(null) }
            var dropdownExpanded by remember { mutableStateOf(false) }

            val filteredCampaigns = remember(searchQuery, allCampaigns) {
                if (searchQuery.isBlank()) allCampaigns
                else allCampaigns.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

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
                    modifier = Modifier
                        .wrapContentSize()
                        .widthIn(max = 500.dp)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Export Campaign Report",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        // Search Field
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                selectedCampaignId = null // Clear ID if user types to allow variant search
                            },
                            label = { Text("Search Campaigns...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E2136),
                                unfocusedContainerColor = Color(0xFF1E2136),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            trailingIcon = {
                                androidx.compose.material3.IconButton(onClick = { dropdownExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = Color.White
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Select from List")
                                }
                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier
                                        .width(300.dp)
                                        .background(Color(0xFF1E2136))
                                ) {
                                    filteredCampaigns.forEach { campaign ->
                                        DropdownMenuItem(
                                            text = { Text(campaign.name, color = Color.White) },
                                            onClick = {
                                                selectedCampaignId = campaign.id
                                                searchQuery = campaign.name
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    selectedCampaignId = null
                                    searchQuery = "All Campaigns"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Select All")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    val finalQuery = if (searchQuery == "All Campaigns") null else searchQuery
                                    viewModel.exportCampaignData(
                                        "csv",
                                        selectedCampaignId,
                                        finalQuery.takeIf { it?.isNotBlank() == true })
                                    showExportDialog = false
                                },
                                modifier = Modifier
                                    .focusRequester(exportButtonFocusRequester)
                                    .clickable {
                                        val finalQuery = if (searchQuery == "All Campaigns") null else searchQuery
                                        viewModel.exportCampaignData(
                                            "csv",
                                            selectedCampaignId,
                                            finalQuery.takeIf { it?.isNotBlank() == true })
                                        showExportDialog = false
                                    }
                            ) {
                                Text("Export CSV")
                            }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val finalQuery = if (searchQuery == "All Campaigns") null else searchQuery
                                    viewModel.exportCampaignData(
                                        "pdf",
                                        selectedCampaignId,
                                        finalQuery.takeIf { it?.isNotBlank() == true })
                                    showExportDialog = false
                                },
                                modifier = Modifier.clickable {
                                    val finalQuery = if (searchQuery == "All Campaigns") null else searchQuery
                                    viewModel.exportCampaignData(
                                        "pdf",
                                        selectedCampaignId,
                                        finalQuery.takeIf { it?.isNotBlank() == true })
                                    showExportDialog = false
                                }
                            ) {
                                Text("Export PDF")
                            }
                            androidx.compose.material3.Button(
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
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                trailing?.invoke()
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
