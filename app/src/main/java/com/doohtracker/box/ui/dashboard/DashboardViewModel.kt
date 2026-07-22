package com.doohtracker.box.ui.dashboard

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.doohtracker.box.data.hardware.UsbConnectionMonitor
import com.doohtracker.box.data.location.LocationMonitor
import com.doohtracker.box.data.logic.VerificationGatekeeper
import com.doohtracker.box.data.repository.CampaignRepository
import com.doohtracker.box.data.system.SystemHealth
import com.doohtracker.box.data.system.SystemHealthMonitor
import com.doohtracker.box.data.sync.SyncManager
import com.doohtracker.box.data.time.TimeProvider
import com.doohtracker.box.tracker.player.PlaybackManager
import com.doohtracker.box.util.ConnectivityMonitor
import com.doohtracker.box.util.PdfSection
import com.doohtracker.box.util.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val campaignRepository: CampaignRepository,
    private val usbConnectionMonitor: UsbConnectionMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
    private val gatekeeper: VerificationGatekeeper,
    private val syncManager: SyncManager,
    private val systemActions: SystemActions,
    private val locationMonitor: LocationMonitor,
    private val systemHealthMonitor: SystemHealthMonitor,
    private val timeProvider: TimeProvider,
    val playbackManager: PlaybackManager
) : ViewModel() {

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()
    private var lastRestartRequestTime = 0L

    init {
        usbConnectionMonitor.onNewPermissionGranted
            .onEach {
                val currentTime = timeProvider.getCurrentTimeMillis()
                if (currentTime - lastRestartRequestTime > 30000L) {
                    _showRestartDialog.value = true
                    lastRestartRequestTime = currentTime
                }
            }
            .launchIn(viewModelScope)
    }

    fun reboot() {
        lastRestartRequestTime = timeProvider.getCurrentTimeMillis()
        systemActions.rebootDevice()
        _showRestartDialog.value = false
    }

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun openLocationSettings() {
        systemActions.openLocationSettings()
    }

    fun openDateTimeSettings() {
        systemActions.openDateTimeSettings()
    }

    val isHdmiActive: StateFlow<Boolean> = usbConnectionMonitor.isUsbDeviceConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isConnected: StateFlow<Boolean> = connectivityMonitor.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastSyncTime: StateFlow<String> = flow {
        while (true) {
            val time = campaignRepository.getLastSyncTime()
            if (time > 0) {
                val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
                emit(sdf.format(Date(time)))
            } else {
                emit("Never")
            }
            delay(10000.milliseconds)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Never")

    private val timeTicker = flow {
        while (true) {
            emit(timeProvider.getCurrentTimeMillis())
            delay(10000.milliseconds)
        }
    }

    val anyCampaignActive: StateFlow<Boolean> = combine(
        campaignRepository.allCampaigns,
        timeTicker
    ) { all, now ->
        all.any { it.startTime <= now && it.endTime >= now }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalCampaignsCount: StateFlow<Int> = combine(
        campaignRepository.allCampaigns,
        timeTicker
    ) { all, now ->
        all.count { it.startTime <= now && it.endTime >= now }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentCampaignName: StateFlow<String> = combine(
        gatekeeper.isTrackingAllowed,
        campaignRepository.allCampaigns,
        timeTicker
    ) { isTrackingAllowed, allCampaigns, currentTime ->
        if (isTrackingAllowed) {
            val activeCampaigns = allCampaigns.filter {
                it.startTime <= currentTime && it.endTime >= currentTime
            }
            if (activeCampaigns.isNotEmpty()) {
                activeCampaigns.joinToString(" | ") { "${it.name} (${it.syncedCount})" }
            } else {
                "No Scheduled Campaigns Now"
            }
        } else {
            "Waiting for HDMI Stream"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Initializing...")

    val recentLogs: StateFlow<List<TrackingLogUiModel>> = combine(
        campaignRepository.recentLogs,
        campaignRepository.allCampaigns
    ) { logs, campaigns ->
        logs.map { log ->
            val campaign = campaigns.find { it.id == log.campaignId }
            TrackingLogUiModel(
                title = campaign?.name ?: "Unknown Campaign",
                timestamp = log.timestamp,
                isSynced = log.isSynced
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val locationStatusTrigger = flow {
        while (true) {
            emit(Unit)
            delay(5000.milliseconds)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLocation: StateFlow<String> = combine(
        locationMonitor.currentLocation,
        locationStatusTrigger
    ) { location, _ -> location }
        .flatMapLatest { location ->
            flow {
                if (location != null) {
                    val coords = String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
                    emit("Resolving Address...\n($coords)")

                    val address = withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())

                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            addresses?.firstOrNull()?.let { addr ->
                                val sb = StringBuilder()
                                for (i in 0..addr.maxAddressLineIndex) {
                                    sb.append(addr.getAddressLine(i)).append(" ")
                                }
                                sb.toString().trim()
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (address != null) {
                        emit("$address\n($coords)")
                    } else {
                        emit("Address Unavailable\n($coords)")
                    }
                } else {
                    val fallbackNotes = campaignRepository.getInstallationNotes()
                    if (fallbackNotes.isNotBlank()) {
                        emit("$fallbackNotes (Manual)")
                    } else if (!locationMonitor.isLocationEnabled) {
                        emit("Location Disabled in Settings")
                    } else {
                        emit("Detecting Location...")
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Detecting...")

    val syncStatus: StateFlow<String> = syncManager.getSyncWorkInfo()
        .map { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.RUNNING -> "Syncing..."
                WorkInfo.State.SUCCEEDED -> "Synced"
                WorkInfo.State.FAILED -> "Sync Failed"
                else -> "Ready"
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ready")

    val systemHealth: StateFlow<SystemHealth?> = systemHealthMonitor.healthInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySpotsSummary: StateFlow<String> = combine(
        campaignRepository.allCampaigns,
        flow {
            while (true) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = timeProvider.getCurrentTimeMillis()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                emit(cal.timeInMillis)
                delay(30000.milliseconds)
            }
        }.flatMapLatest { today -> campaignRepository.getLogsSinceFlow(today) }
    ) { campaigns, logs ->
        // Create a map for quick lookup
        val campaignNames = campaigns.associate { it.id to it.name }

        // Group logs by campaign ID and count them
        val counts = logs.groupBy { it.campaignId }
            .map { (id, logList) ->
                val name = campaignNames[id] ?: "Campaign $id"
                "$name: ${logList.size}"
            }

        if (counts.isEmpty()) {
            "No spots today"
        } else {
            counts.joinToString(" | ")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Calculating...")

    fun exportCampaignData(format: String) {
        viewModelScope.launch {
            val campaigns = campaignRepository.allCampaigns.first()

            val today = Calendar.getInstance().apply {
                timeInMillis = timeProvider.getCurrentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayLogs = campaignRepository.getLogsSince(today)

            // Calculate total spots across all campaigns
            val totalSpots = todayLogs.size

            // Aggregation for summary
            val summaryData = campaigns.mapNotNull { campaign ->
                val count = todayLogs.count { it.campaignId == campaign.id }
                if (count > 0) {
                    Triple(campaign.campaignTag, campaign.name, count.toString())
                } else null
            }

            val timestamp = timeProvider.getCurrentTimeMillis()
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            if (format.lowercase() == "csv") {
                val csvBuilder = StringBuilder()

                // Section 0: Total Summary
                csvBuilder.append("OPEN WINDOW TOTAL SPOTS: $totalSpots\n\n")

                // Section 1: Summary
                csvBuilder.append("AGGREGATED SUMMARY\n")
                csvBuilder.append("Campaign Tag,Campaign Title,Total Spots\n")
                summaryData.forEach { (tag, title, count) ->
                    csvBuilder.append("$tag,${title.replace(",", " ")},$count\n")
                }
                csvBuilder.append("\n") // Separator

                // Section 2: Detailed Logs
                csvBuilder.append("PLAYBACK LOG\n")
                csvBuilder.append("Campaign Tag,Campaign Title,Spots,Date,Time\n")

                todayLogs.forEach { log ->
                    val campaign = campaigns.find { it.id == log.campaignId }
                    if (campaign != null) {
                        val date = Date(log.timestamp)
                        csvBuilder.append("${campaign.campaignTag},")
                        csvBuilder.append("${campaign.name.replace(",", " ")},")
                        csvBuilder.append("1,")
                        csvBuilder.append("${sdfDate.format(date)},")
                        csvBuilder.append("${sdfTime.format(date)}\n")
                    }
                }
                systemActions.saveCsvFile("campaign_compliance_report_$timestamp.csv", csvBuilder.toString())
            } else if (format.lowercase() == "pdf") {
                val sections = mutableListOf<PdfSection>()

                // Total Summary Section
                sections.add(
                    PdfSection(
                        title = "Open Window Total Played",
                        headers = listOf("Description", "Value"),
                        rows = listOf(listOf("Total Spots Played", totalSpots.toString()))
                    )
                )

                // Summary Section
                sections.add(
                    PdfSection(
                        title = "Aggregated Summary",
                        headers = listOf("Campaign Tag", "Title", "Total Spots"),
                        rows = summaryData.map { listOf(it.first, it.second.take(20), it.third) }
                    )
                )

                // Detailed Logs Section
                sections.add(
                    PdfSection(
                        title = "Playback Log",
                        headers = listOf("Campaign Tag", "Title", "Spots", "Date", "Time"),
                        rows = todayLogs.mapNotNull { log ->
                            val campaign = campaigns.find { it.id == log.campaignId }
                            if (campaign != null) {
                                val date = Date(log.timestamp)
                                listOf(
                                    campaign.campaignTag,
                                    campaign.name.take(15),
                                    "1",
                                    sdfDate.format(date),
                                    sdfTime.format(date)
                                )
                            } else null
                        }
                    )
                )

                systemActions.savePdfFile(
                    "campaign_compliance_report_$timestamp.pdf",
                    "Campaign Compliance Report - ${sdfDate.format(Date(timestamp))}",
                    sections
                )
            }
        }
    }
}

data class TrackingLogUiModel(
    val title: String,
    val timestamp: Long,
    val isSynced: Boolean
)
