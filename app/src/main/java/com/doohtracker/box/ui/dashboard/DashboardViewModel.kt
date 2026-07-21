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
import com.doohtracker.box.util.ConnectivityMonitor
import com.doohtracker.box.util.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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
    private val systemHealthMonitor: SystemHealthMonitor
) : ViewModel() {

    private val _showRestartDialog = MutableStateFlow(false)
    val showRestartDialog: StateFlow<Boolean> = _showRestartDialog.asStateFlow()
    private var lastRestartRequestTime = 0L

    init {
        usbConnectionMonitor.onNewPermissionGranted
            .onEach { 
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRestartRequestTime > 30000L) {
                    _showRestartDialog.value = true 
                    lastRestartRequestTime = currentTime
                }
            }
            .launchIn(viewModelScope)
    }

    fun reboot() {
        lastRestartRequestTime = System.currentTimeMillis()
        systemActions.rebootDevice()
        _showRestartDialog.value = false
    }

    fun dismissRestartDialog() {
        _showRestartDialog.value = false
    }

    fun openLocationSettings() {
        systemActions.openLocationSettings()
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

    val anyCampaignActive: StateFlow<Boolean> = flow {
        while (true) {
            val currentTime = System.currentTimeMillis()
            val allCampaigns = campaignRepository.allCampaigns.first()
            val active = allCampaigns.any { it.startTime <= currentTime && it.endTime >= currentTime }
            emit(active)
            delay(10000.milliseconds)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentCampaignName: StateFlow<String> = combine(
        gatekeeper.isTrackingAllowed,
        campaignRepository.allCampaigns,
        flow { 
            while(true) { 
                emit(System.currentTimeMillis())
                delay(10000.milliseconds)
            } 
        }
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

    val totalCampaignsCount: StateFlow<Int> = campaignRepository.allCampaigns
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
}

data class TrackingLogUiModel(
    val title: String,
    val timestamp: Long,
    val isSynced: Boolean
)
