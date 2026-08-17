package com.doohtracker.box.ui.dashboard

import android.content.Context
import android.content.res.Configuration.UI_MODE_TYPE_MASK
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.work.Configuration
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
    val identities by viewModel.identities.collectAsState()
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
    val mismatchedTitle by viewModel.playbackManager.mismatchedTitle.collectAsState()
    val isSourcePreviewEnabled by viewModel.isSourcePreviewEnabled.collectAsState()
    val siteId by viewModel.activationCode.collectAsState()
    val isHubSyncing by viewModel.isHubSyncing.collectAsState()
    val isDarkMode by viewModel.isDarkModeEnabled.collectAsState()
    val isMirrorPreviewActive by viewModel.isMirrorPreviewActive.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var adsSiteIdToShow by remember { mutableStateOf<String?>(null) }

    val restartButtonFocusRequester = remember { FocusRequester() }
    val logoutButtonFocusRequester = remember { FocusRequester() }
    val exportButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
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
                            text = "DOOH Tracker Hub",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Night/Day Mode Toggle
                            Surface(
                                onClick = { viewModel.toggleDarkMode() },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Gray.copy(alpha = 0.2f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = CircleShape
                                    ),
                                    pressedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = CircleShape
                                    )
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable { viewModel.toggleDarkMode() }
                                ) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle Theme",
                                        tint = if (isDarkMode) Color.Yellow else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Source Preview",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSourcePreviewEnabled && isSourceLinked) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Surface(
                                onClick = { viewModel.toggleSourcePreview(!(isSourcePreviewEnabled && isSourceLinked)) },
                                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Gray.copy(alpha = 0.1f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = MaterialTheme.shapes.small
                                    ),
                                    pressedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = MaterialTheme.shapes.small
                                    )
                                ),
                                modifier = Modifier.scale(0.8f)
                            ) {
                                Box(modifier = Modifier.padding(4.dp)) {
                                    Switch(
                                        checked = isSourcePreviewEnabled && isSourceLinked,
                                        onCheckedChange = { viewModel.toggleSourcePreview(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Row 1: IP Address with Add Site ID Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IP Address: $localIp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TvButton(
                            onClick = { viewModel.startAddIdentity() },
                            colors = TvButtonDefaults.colors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White,
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = Color.Black
                            ),
                            border = TvButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                ),
                                pressedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                            ),
                            modifier = Modifier.clickable { viewModel.startAddIdentity() }
                        ) {
                            Text(modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), text = "Add New Site ID")
                        }
                    }

                    // Row 2: Core Connectivity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusCard(
                            title = if (isSourceLinked) "Source Monitor" else "Tracker Status",
                            status = sourceMonitorStatus,
                            color = when {
                                sourceMonitorStatus.contains("Active") -> StatusGreen
                                sourceMonitorStatus == "Ready" -> StatusCyan
                                sourceMonitorStatus == "Content Mismatch" -> Color(0xFFFFA500)
                                sourceMonitorStatus.contains("Internal") -> StatusCyan
                                sourceMonitorStatus == "No Media" -> Color(0xFF87CEEB)
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
                            title = "Primary Site ID",
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

                    // MULTI-SITE LIST SECTION (New, integrated into the scroll flow)
                    if (identities.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp) // Reduced internal spacing
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { /* Informational focus */ },
                                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = Color.White.copy(alpha = 0.05f)
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(1.dp, StatusCyan),
                                            shape = MaterialTheme.shapes.small
                                        ),
                                        pressedBorder = Border(
                                            border = BorderStroke(1.dp, StatusCyan),
                                            shape = MaterialTheme.shapes.small
                                        )
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        modifier = Modifier.padding(8.dp),
                                        text = "Active Multi-Site Identities (${identities.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.Gray
                                    )
                                }

                                TvButton(
                                    onClick = { viewModel.clearData() },
                                    colors = TvButtonDefaults.colors(
                                        containerColor = Color.Transparent,
                                        contentColor = StatusRed,
                                        focusedContainerColor = StatusRed.copy(alpha = 0.1f),
                                        focusedContentColor = StatusRed
                                    ),
                                    border = TvButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(1.dp, StatusCyan),
                                            shape = MaterialTheme.shapes.extraSmall
                                        ),
                                        pressedBorder = Border(
                                            border = BorderStroke(1.dp, StatusCyan),
                                            shape = MaterialTheme.shapes.extraSmall
                                        )
                                    ),
                                    modifier = Modifier
                                        .scale(0.8f)
                                        .clickable { viewModel.clearData() }
                                ) {
                                    Text("Clear Hub")
                                }
                            }

                            identities.forEach { identity ->
                                IdentityHubCard(
                                    identity = identity,
                                    onRemove = { viewModel.removeSite(it) },
                                    onInspect = { adsSiteIdToShow = identity.siteId }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val spots by viewModel.dailySpotsSummary.collectAsState()
                        StatusCard(
                            title = "Total Spots Today",
                            info = "Export to PDF/CSV",
                            status = spots,
                            color = StatusCyan,
                            modifier = Modifier.fillMaxWidth(),
                            isScrolling = true,
                            onClick = {
                                if (spots == "No spots today") {
                                    Toast.makeText(context, "No spots to export today", Toast.LENGTH_LONG).show()
                                } else showExportDialog = true
                            }
                        )
                    }

                    // Row 4: Location and Sync
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

                        StatusCard(
                            title = "Last Sync",
                            status = lastSyncTime,
                            color = StatusCyan,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.openDateTimeSettings() }
                        )
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

                    // Bottom Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvButton(
                            onClick = { showLogoutDialog = true },
                            colors = TvButtonDefaults.colors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White,
                                focusedContainerColor = StatusRed,
                                focusedContentColor = Color.White
                            ),
                            border = TvButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                ),
                                pressedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                            ),
                            modifier = Modifier.clickable { showLogoutDialog = true }
                        ) {
                            Text(modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp), text = "Wipe All Data")
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
                            isMirrorPreviewActive -> {
                                // Forced Preview Mode
                                val url = campaign?.mediaUrl?.lowercase() ?: ""
                                if (campaign?.mediaType?.lowercase() == "image" ||
                                    url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                                    url.endsWith(".png") || url.endsWith(".webp")
                                ) "image" else "video"
                            }

                            // 4. Remain visible if syncing OR if "No Media" / "Ready" (Idle)
                            isHubSyncing || !isSourcePreviewEnabled || 
                            sourceMonitorStatus == "No Media" || sourceMonitorStatus == "Ready" || 
                            sourceMonitorStatus.contains("Idle") -> "reference"

                            mismatchedTitle != null -> "mismatch"
                            campaign != null && campaign.localPath != null -> {
                                val url = campaign.mediaUrl?.lowercase() ?: ""
                                if (campaign.mediaType?.lowercase() == "image" ||
                                    url.endsWith(".jpg") || url.endsWith(".jpeg") ||
                                    url.endsWith(".png") || url.endsWith(".webp")
                                ) "image" else "video"
                            }

                            isSourceLinked -> "mirror"
                            else -> "none"
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (mediaType) {
                                "reference" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E2136)),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "Campaign Reference",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = StatusCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            identities.forEach { identity ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                    identity.siteId,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White
                                                )
                                                
                                                // 3. Per-Identity Sync Status
                                                val statusText = if (identity.isDownloading) "Downloading..." else "Done"
                                                
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (statusText == "Done") StatusGreen else StatusYellow,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                }
                                            }
                                        }
                                    }
                                }

                                "mirror" -> {
                                    SourceMonitorPreview(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1E2136)),
                                        signalMonitor = viewModel.signalMonitor,
                                        siteId = currentMediaCampaign?.campaignTag
                                    )
                                }

                                "mismatch" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF2C1E1E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "UNAUTHORIZED CONTENT",
                                                color = StatusRed,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                mismatchedTitle ?: "",
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                "image" -> {
                                    val imageModel = currentMediaCampaign?.localPath ?: currentMediaCampaign?.mediaUrl
                                    AsyncImage(
                                        model = imageModel,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                "video" -> {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                player = playbackManager.player
                                                useController = false
                                                controllerAutoShow = false
                                                @Suppress("DEPRECATION")
                                                useArtwork = false
                                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                                setBackgroundColor(android.graphics.Color.BLACK)
                                                setKeepContentOnPlayerReset(true)
                                                
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
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
                                            when {
                                                !isSourceLinked -> "Waiting for Source Monitor Link..."
                                                identities.any { it.status == "No Media" } -> "Waiting for valid media transmission..."
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
                    }

                    Text(
                        text = "Audit Trail",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showLogoutDialog = true },
                        shape = RectangleShape,
                        colors = SurfaceDefaults.colors(containerColor = Color(0xFF1E2136), contentColor = Color.White)
                    ) {
                        LogsList(logs)
                    }
                }
            }
        }

        // Dialogs
        if (showLogoutDialog) {
            LogoutDialog(
                onDismiss = { showLogoutDialog = false },
                onConfirm = { viewModel.clearData() },
                focusRequester = logoutButtonFocusRequester
            )
        }
        if (showRestartDialog) {
            RestartDialog(
                onDismiss = { viewModel.dismissRestartDialog() },
                onConfirm = { viewModel.reboot() },
                focusRequester = restartButtonFocusRequester
            )
        }
        if (showExportDialog) {
            ExportDialog(
                viewModel = viewModel,
                onDismiss = { showExportDialog = false },
                focusRequester = exportButtonFocusRequester
            )
        }
        if (adsSiteIdToShow != null) {
            val identity = identities.find { it.siteId == adsSiteIdToShow }
            if (identity != null) {
                AllAdsDialog(
                    identity = identity,
                    onDismiss = { adsSiteIdToShow = null },
                    viewModel = viewModel
                )
            }
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IdentityHubCard(identity: IdentityUiModel, onRemove: (String) -> Unit, onInspect: () -> Unit) {
    val isDarkMode = MaterialTheme.colorScheme.surface == Color.Black || MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        onClick = onInspect,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isDarkMode) Color(0xFF1E2136) else Color(0xFFF0F2F5),
            contentColor = if (isDarkMode) Color.White else Color.Black,
            focusedContainerColor = if (isDarkMode) Color(0xFF2D324D) else Color(0xFFE2E8F0)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, StatusCyan),
                shape = MaterialTheme.shapes.medium
            ),
            pressedBorder = Border(
                border = BorderStroke(1.dp, StatusCyan),
                shape = MaterialTheme.shapes.medium
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onInspect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(0.8f)) {
                Text(
                    text = identity.siteId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDarkMode) Color.White else Color.Black.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Text(text = "User: ${identity.username}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Column(modifier = Modifier.weight(1.5f)) {
                Row {
                    Text(
                        text = identity.status,
                        color = identity.statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(" | ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = identity.mediaTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (identity.isMismatched) StatusRed else Color.Gray,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp)
                    )
                }
                AutoScrollingText(
                    text = identity.currentCampaign,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Inspect All References Button (Shown when "Active" or "Content Mismatch")
                if ((identity.status == "Active" || identity.status == "Content Mismatch") && LocalContext.current.isMobileDevice()) {
                    Surface(
                        onClick = onInspect,
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = StatusCyan,
                            focusedContainerColor = StatusCyan.copy(alpha = 0.2f)
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = CircleShape
                            )
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onInspect() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Inspect All References",
                                tint = StatusCyan,
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }

                // Site ID Remove Button
                Surface(
                    onClick = { onRemove(identity.siteId) },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = StatusRed,
                        focusedContainerColor = StatusRed.copy(alpha = 0.2f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(1.dp, StatusCyan),
                            shape = CircleShape
                        )
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onRemove(identity.siteId) }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Site ID",
                            tint = Color.Gray,
                            modifier = Modifier.scale(0.8f)
                        )
                        Text("✕", color = Color.White.copy(alpha = 0.1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

fun Context.isMobileDevice(): Boolean {
    val uiMode = resources.configuration.uiMode and UI_MODE_TYPE_MASK

    return uiMode != UI_MODE_TYPE_TELEVISION
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
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        onClick = onClick ?: {},
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
//            containerColor = if (isDarkMode) Color(0xFF1E2136) else Color(0xFFF0F2F5),
            containerColor = Color(0xFF1E2136),
            contentColor = if (isDarkMode) Color.White else Color.Black,
            focusedContainerColor = if (isDarkMode) Color(0xFF2D324D) else Color(0xFFE2E8F0)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, StatusCyan),
                shape = MaterialTheme.shapes.medium
            ),
            pressedBorder = Border(
                border = BorderStroke(1.dp, StatusCyan),
                shape = MaterialTheme.shapes.medium
            )
        ),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        StatusCardContent(title, info, status, color, isScrolling, trailing)
    }
}

@Composable
private fun StatusCardContent(
    title: String,
    info: String?,
    status: String,
    color: Color,
    isScrolling: Boolean,
    trailing: @Composable (() -> Unit)?
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
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (info != null) Text(text = info, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            trailing?.invoke()
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (isScrolling) AutoScrollingText(text = status, color = color, style = MaterialTheme.typography.bodyLarge)
        else Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AutoScrollingText(text: String, color: Color, style: androidx.compose.ui.text.TextStyle) {
    val scrollState = rememberScrollState()
    var interactionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(text, interactionCount) {
        if (scrollState.maxValue > 0) {
            // If user just interacted, wait 1 second. 
            // Otherwise, we start the normal cycle.
            if (interactionCount > 0) {
                delay(1000.milliseconds)
            }

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
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, enabled = true)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        interactionCount++
                    }
                }
            }
    ) {
        Text(text = text, style = style, color = color, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.2f)), horizontalArrangement = Arrangement.SpaceBetween
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
                    textAlign = TextAlign.End,
                    color = Color.Gray
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log -> LogItem(log) }
            }
        }
    }
}

@Composable
fun LogItem(log: TrackingLogUiModel) {
    val dateSdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    val timeSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = log.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = Color.White
        )
        Text(
            text = dateSdf.format(Date(log.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f),
            color = Color.LightGray
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = timeSdf.format(Date(log.timestamp)), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (log.isComplianceRejected) "❌" else if (log.isSynced) "✓" else "☁",
                color = if (log.isComplianceRejected) StatusRed else if (log.isSynced) StatusGreen else Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, focusRequester: FocusRequester) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center
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
                Text(text = "Re-activate Device?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "This will wipe all device credentials, delete downloaded campaigns, and clear all logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(
                        onClick = onConfirm,
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .clickable { onConfirm() }
                    ) { Text("Logout & Wipe") }
                    TvButton(
                        onClick = onDismiss,
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier.clickable { onDismiss() }
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RestartDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, focusRequester: FocusRequester) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center
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
                Text(text = "Restart Required", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "A new USB Video device has been authorized. Please restart the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(
                        onClick = onConfirm,
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .clickable { onConfirm() }
                    ) { Text("Restart Now") }
                    TvButton(
                        onClick = onDismiss,
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier.clickable { onDismiss() }
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ExportDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit, focusRequester: FocusRequester) {
    val allCampaigns by viewModel.allCampaignsList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCampaignId by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val filteredCampaigns = remember(searchQuery, allCampaigns) {
        if (searchQuery.isBlank()) allCampaigns else allCampaigns.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center
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
                Text(text = "Export Campaign Report", style = MaterialTheme.typography.headlineMedium)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; selectedCampaignId = null },
                    label = { Text("Search Campaigns...") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { dropdownExpanded = true }) {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                null
                            )
                        }
                    })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        TvButton(
                            onClick = { dropdownExpanded = true },
                            border = TvButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                ),
                                pressedBorder = Border(
                                    border = BorderStroke(1.dp, StatusCyan),
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Select from List") }
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
                                        selectedCampaignId = campaign.id; searchQuery = campaign.name; dropdownExpanded = false
                                    })
                            }
                        }
                    }
                    TvButton(
                        onClick = { selectedCampaignId = null; searchQuery = "All Campaigns" },
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedCampaignId = null; searchQuery = "All Campaigns" }
                    ) { Text("Select All") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        onClick = {
                            viewModel.exportCampaignData(
                                "csv",
                                selectedCampaignId,
                                if (searchQuery == "All Campaigns") null else searchQuery
                            ); onDismiss()
                        },
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .clickable {
                                viewModel.exportCampaignData(
                                    "csv",
                                    selectedCampaignId,
                                    if (searchQuery == "All Campaigns") null else searchQuery
                                ); onDismiss()
                            }
                    ) { Text("CSV") }
                    TvButton(
                        onClick = {
                            viewModel.exportCampaignData(
                                "pdf",
                                selectedCampaignId,
                                if (searchQuery == "All Campaigns") null else searchQuery
                            ); onDismiss()
                        },
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier.clickable {
                            viewModel.exportCampaignData(
                                "pdf",
                                selectedCampaignId,
                                if (searchQuery == "All Campaigns") null else searchQuery
                            ); onDismiss()
                        }
                    ) { Text("PDF") }
                    TvButton(
                        onClick = onDismiss,
                        border = TvButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                            pressedBorder = Border(
                                border = BorderStroke(1.dp, StatusCyan),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                        ),
                        modifier = Modifier.clickable { onDismiss() }
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AllAdsDialog(identity: IdentityUiModel, onDismiss: () -> Unit, viewModel: DashboardViewModel) {
    val isMirrorPreviewActive by viewModel.isMirrorPreviewActive.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF1E2136)),
            modifier = Modifier
                .wrapContentSize()
                .widthIn(max = 550.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "References for ${identity.siteId}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = StatusCyan,
                    fontWeight = FontWeight.Bold
                )

                if (identity.campaigns.isEmpty()) {
                    Text("No campaigns found for this site.", color = Color.Gray, modifier = Modifier.padding(vertical = 10.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        identity.campaigns.forEach { brief ->
                            Surface(
                                onClick = { if (brief.isVerified) viewModel.previewCampaign(brief.id) },
                                shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = MaterialTheme.shapes.small
                                    ),
                                    pressedBorder = Border(
                                        border = BorderStroke(1.dp, StatusCyan),
                                        shape = MaterialTheme.shapes.small
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (brief.isVerified) 1f else 0.5f)
                                    .clickable { if (brief.isVerified) viewModel.previewCampaign(brief.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            "• ",
                                            color = if (brief.isVerified) StatusCyan else Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = brief.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    if (brief.isVerified) {
                                        Text(
                                            text = if (isMirrorPreviewActive && viewModel.playbackManager.currentCampaign.collectAsState().value?.id == brief.id) "Previewing..." else "TAP TO PREVIEW",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "PENDING...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TvButton(
                    onClick = onDismiss,
                    colors = TvButtonDefaults.colors(
                        containerColor = Color.DarkGray,
                        contentColor = Color.White,
                        focusedContainerColor = StatusCyan,
                        focusedContentColor = Color.Black
                    ),
                    border = TvButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(1.dp, StatusCyan),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                    ),
                    modifier = Modifier.clickable { onDismiss() }
                ) { Text("Close") }
            }
        }
    }
}
