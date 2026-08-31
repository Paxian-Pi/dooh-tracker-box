package com.doohtracker.box.data.repository

import com.doohtracker.box.data.local.CampaignDao
import com.doohtracker.box.data.local.CampaignEntity
import com.doohtracker.box.data.local.TrackingLogDao
import com.doohtracker.box.data.local.TrackingLogEntity
import com.doohtracker.box.data.manager.DeviceManager
import com.doohtracker.box.data.manager.Identity
import com.doohtracker.box.data.model.SendProofOfPlayRequest
import com.doohtracker.box.data.model.TrackHourlyRequest
import com.doohtracker.box.data.model.toEntity
import com.doohtracker.box.data.remote.CampaignApi
import com.doohtracker.box.data.remote.MediaDownloader
import com.doohtracker.box.data.time.TimeProvider
import com.doohtracker.box.data.util.baseUrl
import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.data.hardware.SourceMetadata
import com.doohtracker.box.data.hardware.InternalMediaMonitor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampaignRepository @Inject constructor(
    private val api: CampaignApi,
    private val campaignDao: CampaignDao,
    private val trackingLogDao: TrackingLogDao,
    val deviceManager: DeviceManager,
    private val signalMonitor: SignalMonitor,
    val internalMediaMonitor: InternalMediaMonitor,
    private val mediaDownloader: MediaDownloader,
    private val timeProvider: TimeProvider
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    
    /**
     * Memory-cached list of all campaigns to avoid frequent DB hits in high-frequency loops.
     */
    val cachedCampaigns: StateFlow<List<CampaignEntity>> = allCampaigns
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val recentLogs: Flow<List<TrackingLogEntity>> = trackingLogDao.getRecentLogs()
    val identities: Flow<List<Identity>> = deviceManager.identitiesFlow
    val isDarkModeEnabled: Flow<Boolean> = deviceManager.isDarkModeEnabledFlow
    val isLocalMonitoringEnabled: Flow<Boolean> = deviceManager.isLocalMonitoringEnabledFlow

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isDownloadingFiles = MutableStateFlow(false)
    val isDownloadingFiles: StateFlow<Boolean> = _isDownloadingFiles.asStateFlow()

    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap()) // campaignId -> error message
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val isHubSyncing: Flow<Boolean> = allCampaigns.map { campaigns ->
        val now = timeProvider.getCurrentTimeMillis()
        val hasMissingActiveFiles = campaigns.any { 
            val url = it.mediaUrl?.lowercase() ?: ""
            val isVideo = it.mediaType?.lowercase() == "video" || url.endsWith(".mp4")
            // A file is only "missing" if it's NOT a video AND has no local path
            !isVideo && it.startTime <= now && it.endTime >= now && it.mediaUrl != null && it.localPath == null
        }
        val hasNoCampaigns = campaigns.isEmpty() && deviceManager.isActivated()
        
        hasNoCampaigns || hasMissingActiveFiles
    }.distinctUntilChanged()
    .debounce { syncing ->
        if (syncing) 0L else 2000L // 2s stabilization buffer
    }

    // Hub Tracking: Keyed by Site ID
    private val activeSessions = mutableMapOf<String, PlaybackSession>()
    private var lastPulseTime: Long = 0L
    private var lastSyncAttemptedTitle: String? = null

    private data class PlaybackSession(
        var campaignId: String?,
        var startTime: Long
    )

    fun isDeviceActivated(): Boolean = deviceManager.isActivated()
    fun getActivationCodes(): List<String> = deviceManager.getIdentities().map { it.siteId }
    fun getInstallationNotes(): String = deviceManager.getInstallationNotes()
    fun getLastSyncTime(): Long = deviceManager.getLastSyncTime()

    fun isSourcePreviewEnabled(): Boolean = deviceManager.isSourcePreviewEnabled()
    fun setSourcePreviewEnabled(enabled: Boolean) = deviceManager.setSourcePreviewEnabled(enabled)

    suspend fun activateDevice(code: String, user: String, notes: String): Result<Unit> {
        return try {
            val mediaResponse = api.getMedia()
            if (!mediaResponse.isSuccessful) {
                return Result.failure(Exception("Could not reach backend for validation"))
            }

            val results = mediaResponse.body()?.results ?: emptyList()
            val matchedCampaigns = results.filter { it.campaignTag == code && it.user == user }

            if (matchedCampaigns.isNotEmpty()) {
                deviceManager.addIdentity(Identity(user, code, notes))
                deviceManager.updateLastSyncTime(timeProvider.getCurrentTimeMillis())
                
                val entities = matchedCampaigns.map { it.toEntity().normalizeUrl() }
                campaignDao.insertCampaigns(entities)

                CoroutineScope(Dispatchers.IO).launch {
                    syncCampaigns()
                }

                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid Activation Code or Username for this device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveCampaigns(currentTime: Long): Flow<List<CampaignEntity>> = 
        campaignDao.getActiveCampaigns(currentTime)

    suspend fun getLogsSince(since: Long): List<TrackingLogEntity> = 
        trackingLogDao.getLogsSince(since)

    fun getLogsSinceFlow(since: Long): Flow<List<TrackingLogEntity>> = 
        trackingLogDao.getLogsSinceFlow(since)

    suspend fun syncCampaigns() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        try {
            val mediaResponse = api.getMedia()
            if (mediaResponse.isSuccessful) {
                deviceManager.updateLastSyncTime(timeProvider.getCurrentTimeMillis())
                val mediaResults = mediaResponse.body()?.results ?: emptyList()
                val identities = deviceManager.getIdentities()
                val now = timeProvider.getCurrentTimeMillis()

                identities.forEach { identity ->
                    // 1. Filter: Strictly affiliated AND within open window
                    val matchedResults = mediaResults.filter { dto ->
                        dto.campaignTag == identity.siteId && 
                        dto.user == identity.username &&
                        dto.toEntity().let { it.startTime <= now && it.endTime >= now }
                    }

                    if (matchedResults.isNotEmpty()) {
                        val existingCampaigns = cachedCampaigns.value
                        
                        val identityEntities = matchedResults.map { dto ->
                            val entity = dto.toEntity().normalizeUrl()
                            val existing = existingCampaigns.find { it.id == entity.id }
                            if (existing != null) {
                                entity.copy(
                                    syncedCount = existing.syncedCount,
                                    sourceFilename = existing.sourceFilename,
                                    localPath = existing.localPath,
                                    lastSeenOnBillboardAt = existing.lastSeenOnBillboardAt
                                )
                            } else {
                                entity.copy(
                                    sourceFilename = entity.mediaUrl?.substringAfterLast("/")?.substringBefore("?")
                                )
                            }
                        }
                        campaignDao.insertCampaigns(identityEntities)

                        // 2. Download using URL filename
                        identityEntities.forEach { entity ->
                            if (entity.mediaUrl != null && entity.localPath == null) {
                                // SKIP VIDEO DOWNLOADS: We only verify titles for videos now.
                                val url = entity.mediaUrl.lowercase()
                                val isVideo = entity.mediaType?.lowercase() == "video" || url.endsWith(".mp4")
                                
                                if (isVideo) {
                                    Log.d("Sync", "Skipping video download for '${entity.name}': Verified via Title Only.")
                                    return@forEach
                                }

                                if (!_isDownloadingFiles.value) {
                                    _isDownloadingFiles.value = true
                                }
                                
                                // Extract filename from URL (e.g. DJ___DOOH___TBS_Tower.mp4)
                                val urlFilename = entity.mediaUrl.substringAfterLast("/").substringBefore("?")
                                Log.d("Sync", "Downloading media for campaign '${entity.name}' from: ${entity.mediaUrl}")
                                
                                val file = mediaDownloader.downloadMedia(entity.mediaUrl, urlFilename)
                                
                                if (file != null && file.exists() && file.length() > 0) {
                                    Log.d("Sync", "SUCCESS: File downloaded for '${entity.name}': ${file.absolutePath} (${file.length()} bytes)")
                                    campaignDao.insertCampaigns(listOf(entity.copy(localPath = file.absolutePath)))
                                    
                                    // Clear any previous error for this campaign
                                    val updatedErrors = _downloadErrors.value.toMutableMap()
                                    updatedErrors.remove(entity.id)
                                    _downloadErrors.value = updatedErrors
                                } else {
                                    val errorMsg = when {
                                        file == null -> "Downloader returned null"
                                        !file.exists() -> "Downloaded file does not exist: ${file.absolutePath}"
                                        file.length() == 0L -> "Downloaded file is empty (0 bytes): ${file.absolutePath}"
                                        else -> "Unknown download error"
                                    }
                                    Log.e("Sync", "FAILED: Download failed for campaign '${entity.name}' ($errorMsg). URL: ${entity.mediaUrl}")
                                    
                                    // Track the error
                                    val updatedErrors = _downloadErrors.value.toMutableMap()
                                    updatedErrors[entity.id] = errorMsg
                                    _downloadErrors.value = updatedErrors
                                }
                            }
                        }
                    }
                }
            }
            val startOfToday = Calendar.getInstance().apply {
                timeInMillis = timeProvider.getCurrentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            campaignDao.deleteExpiredCampaigns(startOfToday)
        } catch (e: Exception) {
            Log.e("Sync", "FATAL: Error during synchronization - ${e.message}", e)
        } finally {
            _isSyncing.value = false
            _isDownloadingFiles.value = false
        }
    }

    fun clearDownloadError(campaignId: String) {
        val updatedErrors = _downloadErrors.value.toMutableMap()
        updatedErrors.remove(campaignId)
        _downloadErrors.value = updatedErrors
        Log.d("Sync", "Cleared download error for campaign: $campaignId")
    }

    fun clearAllDownloadErrors() {
        _downloadErrors.value = emptyMap()
        Log.d("Sync", "Cleared all download errors")
    }

    private fun CampaignEntity.normalizeUrl(): CampaignEntity {
        val url = mediaUrl ?: return this
        val normalized = if (!url.startsWith("http")) {
            val domain = baseUrl.substringBefore("/api/").removeSuffix("/")
            val path = if (url.startsWith("/")) url else "/$url"
            "$domain$path"
        } else url
        
        // Final sanity check for double slashes in path (excluding protocol)
        val finalUrl = normalized.replace(Regex("(?<!:)/{2,}"), "/")
        Log.d("Sync", "Normalized Media URL: $finalUrl")
        return this.copy(mediaUrl = finalUrl)
    }

    suspend fun processBillboardPulse(currentTime: Long, metadata: SourceMetadata, locationString: String) {
        val reportedTitle = metadata.mediaTitle.lowercase()
        val allCampaignsNow = cachedCampaigns.value

        val reportingSiteId = metadata.siteId.uppercase()
        val siteCampaigns = allCampaignsNow.filter { 
            it.campaignTag.equals(reportingSiteId, ignoreCase = true) &&
            it.startTime <= currentTime && it.endTime >= currentTime 
        }

        val matchedCampaign = siteCampaigns.find { campaign ->
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val campaignName = campaign.name.lowercase()
            (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || reportedTitle.contains(campaignName)
        }?.takeIf { reportedTitle != "none" }

        val newCampaignId = matchedCampaign?.id
        val currentSession = activeSessions[reportingSiteId]

        // 1b. RECOGNITION TRIGGER: If unrecognized title detected, attempt immediate sync to fetch new reference
        if (matchedCampaign == null && reportedTitle != "none" && reportedTitle != lastSyncAttemptedTitle) {
            lastSyncAttemptedTitle = reportedTitle
            Log.d("Tracking", "Unrecognized title detected on $reportingSiteId: $reportedTitle. Attempting immediate reference sync.")
            CoroutineScope(Dispatchers.IO).launch {
                syncCampaigns()
            }
        } else if (matchedCampaign != null) {
            // Reset attempt tracker if we found a match
            lastSyncAttemptedTitle = null
        }

        // 2. Logic: Handle Session Changes OR Hourly Boundary Crossings
        val currentHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(currentTime))
        val sessionStartHour = if ((currentSession?.startTime ?: 0L) > 0L) {
            SimpleDateFormat("HH", Locale.getDefault()).format(Date(currentSession!!.startTime))
        } else currentHour

        val hasContentChanged = newCampaignId != currentSession?.campaignId
        val hasHourChanged = currentHour != sessionStartHour

        if (hasContentChanged || hasHourChanged) {
            // A. Finalize previous session for this client
            if (currentSession?.campaignId != null) {
                val finishedCampaign = allCampaignsNow.find { it.id == currentSession.campaignId }
                if (finishedCampaign != null) {
                    val durationSeconds = (currentTime - currentSession.startTime) / 1000L
                    if (durationSeconds >= 1L) { // report any visibility >= 1s
                        Log.d("Tracking", "FINALIZING session for ${finishedCampaign.name} (${durationSeconds}s) on $reportingSiteId")
                        
                        // LOG AUDIT TRAIL IMMEDIATELY (Before network calls)
                        val logId = trackingLogDao.insertLog(
                            TrackingLogEntity(
                                campaignId = finishedCampaign.id,
                                timestamp = currentTime,
                                isSynced = false // Will be updated if sync succeeds
                            )
                        )
                        Log.d("Tracking", "Audit Trail: Log entry created with ID $logId")
                        
                        // SYNC TO CLOUD IMMEDIATELY
                        sendTrackingPulse(finishedCampaign, currentTime, locationString, durationSeconds, logId.toInt(), true)
                    } else {
                        Log.d("Tracking", "Session for ${finishedCampaign.name} too short (${durationSeconds}s). Skipping log.")
                    }
                }
            }

            // B. Start new session for this client
            if (newCampaignId != null) {
                activeSessions[reportingSiteId] = PlaybackSession(newCampaignId, currentTime)
                Log.d("Tracking", "STARTED new session for ${matchedCampaign?.name} on site $reportingSiteId")
                campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
            } else {
                Log.d("Tracking", "No active campaign matched for site $reportingSiteId. Media Title: $reportedTitle")
                activeSessions.remove(reportingSiteId)
            }
        } else if (newCampaignId != null) {
            // C. Maintain current session
            campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
        }

        lastPulseTime = currentTime
    }

    suspend fun trackCurrentCampaigns(currentTime: Long, locationString: String) {
        val activeCampaigns = cachedCampaigns.value.filter { 
            it.startTime <= currentTime && it.endTime >= currentTime 
        }
        val allMetadataMap = signalMonitor.allSourceMetadata.value
        
        if (allMetadataMap.isEmpty()) {
            Log.d("Tracking", "No active source monitor link. Skipping background compliance tracking.")
            return
        }

        // Per-Site Targeted Compliance Tracking
        val activeSiteIds = activeCampaigns.map { it.campaignTag }.distinct()
        
        activeSiteIds.forEach { siteId ->
            val metadata = allMetadataMap[siteId]
            val reportedTitle = metadata?.mediaTitle?.lowercase() ?: "none"
            val siteCampaigns = activeCampaigns.filter { it.campaignTag == siteId }

            val rotationGracePeriod = 10 * 60 * 1000L 
            val suspensionThreshold = 20 * 60 * 1000L

            siteCampaigns.forEach { campaign ->
                val fileName = campaign.sourceFilename?.lowercase() ?: ""
                val campaignName = campaign.name.lowercase()
                val isCurrentMatch = (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                                     reportedTitle.contains(campaignName)

                if (isCurrentMatch && reportedTitle != "none") {
                    campaignDao.updateLastSeenOnBillboardAt(campaign.id, currentTime)
                }

                if (!isCurrentMatch || reportedTitle == "none") {
                    val timeSinceSeen = currentTime - campaign.lastSeenOnBillboardAt
                    val isMissingFromRotation = campaign.lastSeenOnBillboardAt != 0L && timeSinceSeen > rotationGracePeriod
                    val isNewAndNotShowing = campaign.lastSeenOnBillboardAt == 0L && timeSinceSeen > rotationGracePeriod

                    if ((isMissingFromRotation || isNewAndNotShowing) && timeSinceSeen < suspensionThreshold) {
                        // CRITICAL FIX: Only log rejected if we ARE receiving pulses for THIS SPECIFIC site
                        if (metadata != null && reportedTitle != "none") {
                            Log.w("Tracking", "Compliance Rejected for ${campaign.name} on site $siteId. Billboard playing: $reportedTitle")
                            trackingLogDao.insertLog(
                                TrackingLogEntity(
                                    campaignId = campaign.id,
                                    timestamp = currentTime,
                                    isSynced = false,
                                    isComplianceRejected = true
                                )
                            )
                        }
                    }
                }
            }
        }

        // Handle Heartbeat Timeout across all sessions
        if (currentTime - lastPulseTime > 15000 && activeSessions.isNotEmpty()) {
            Log.d("Tracking", "Billboard signal lost for all hub sessions.")
            activeSessions.keys.toList().forEach { sid ->
                processBillboardPulse(currentTime, SourceMetadata(siteId = sid, mediaTitle = "none"), locationString)
            }
        }
    }

    private suspend fun sendTrackingPulse(
        campaign: CampaignEntity,
        currentTime: Long,
        locationString: String,
        durationSeconds: Long,
        localLogId: Int? = null,
        bypassContentCheck: Boolean = false
    ) {
        if (!bypassContentCheck) {
            val allMetadata = signalMonitor.allSourceMetadata.first()
            val metadata = allMetadata[campaign.campaignTag] // TARGETED CHECK
            val reportedTitle = metadata?.mediaTitle?.lowercase() ?: ""
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val isMatch = (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                         reportedTitle.contains(campaign.name.lowercase())
            
            if (!isMatch || reportedTitle == "none") {
                Log.d("Tracking", "Pulse rejected for ${campaign.name}: Content Mismatch or No Signal for Site ${campaign.campaignTag}")
                return
            }
        }

        val now = Date(currentTime)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
        val calendar = Calendar.getInstance().apply { time = now }
        
        val weekDayNum = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "1"
            Calendar.TUESDAY -> "2"
            Calendar.WEDNESDAY -> "3"
            Calendar.THURSDAY -> "4"
            Calendar.FRIDAY -> "5"
            Calendar.SATURDAY -> "6"
            Calendar.SUNDAY -> "7"
            else -> "1"
        }

        val location = locationString.ifBlank { deviceManager.getInstallationNotes() }

        // 1. Send Proof of Play (The "Spot")
        val popRequest = SendProofOfPlayRequest(
            user = campaign.user,
            campaign = campaign.campaign,
            campaignTag = campaign.campaignTag,
            time = timeFormat.format(now),
            date = dateFormat.format(now),
            day = dayFormat.format(now),
            comment = "Automatic tracking",
            location = location,
            uniqueID = campaign.id,
            durationCount = durationSeconds,
            confidence = 1.0f
        )

        try {
            Log.d("Tracking", "Syncing Spot to Cloud for ${campaign.name}...")
            val popResponse = api.sendProofOfPlay(popRequest)
            
            // Fetch updated campaign to get new count for hourly sync
            val latestAll = campaignDao.getAllCampaigns().first()
            val updatedCampaign = latestAll.find { it.id == campaign.id } ?: campaign

            if (popResponse.isSuccessful) {
                // Update Audit Trail state to Synced
                localLogId?.let { id ->
                    trackingLogDao.getLogById(id)?.let { log ->
                        trackingLogDao.updateLog(log.copy(isSynced = true))
                    }
                }
                campaignDao.incrementSyncedCount(campaign.id)
                Log.d("Tracking", "PoP SUCCESS for ${campaign.name}. Count: ${updatedCampaign.syncedCount + 1}")
            } else {
                Log.e("Tracking", "PoP FAILED (HTTP ${popResponse.code()}) for ${campaign.name}")
            }

            // ALWAYS attempt hourly sync to keep counts accurate even if PoP fails
            val hourlyRequest = TrackHourlyRequest(
                user = updatedCampaign.user,
                campaign = updatedCampaign.campaign,
                campaignTag = updatedCampaign.campaignTag,
                hours = hourFormat.format(now),
                date = dateFormat.format(now),
                count = if (popResponse.isSuccessful) updatedCampaign.syncedCount + 1 else updatedCampaign.syncedCount,
                weekDay = weekDayNum,
                location = location,
                uniqueID = updatedCampaign.id
            )
            
            Log.d("Tracking", "Syncing Hourly Count to Cloud for ${campaign.name}...")
            val hourlyResp = api.trackHourly(hourlyRequest)
            if (hourlyResp.isSuccessful) {
                campaignDao.updateLastHourlyTrackedAt(updatedCampaign.id, currentTime)
                Log.d("Tracking", "Hourly Sync SUCCESS for ${campaign.name}")
            } else {
                Log.e("Tracking", "Hourly Sync FAILED (HTTP ${hourlyResp.code()}) for ${campaign.name}")
            }

        } catch (e: Exception) {
            Log.e("Tracking", "Tracking sync FATAL for ${campaign.id}: ${e.message}")
        }
    }

    suspend fun syncPendingLogs() {
        val unsyncedLogs = trackingLogDao.getUnsyncedLogs()

        unsyncedLogs.forEach { log ->
            val campaign = campaignDao.getAllCampaigns().first().find { it.id == log.campaignId }
            if (campaign != null) {
                sendTrackingPulse(
                    campaign = campaign,
                    currentTime = log.timestamp,
                    locationString = deviceManager.getInstallationNotes(),
                    durationSeconds = 0, // Fallback
                    localLogId = log.id,
                    bypassContentCheck = true
                )
            }
        }
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        trackingLogDao.deleteOldSyncedLogs(oneDayAgo)
    }

    suspend fun clearLocalPath(campaignId: String) {
        campaignDao.clearLocalPath(campaignId)
    }

    suspend fun clearData() {
        deviceManager.logout()
        campaignDao.clearAll()
        trackingLogDao.clearAll()
        mediaDownloader.clearMedia()
        
        // Clear in-memory tracking state
        activeSessions.clear()
        lastPulseTime = 0L
        lastSyncAttemptedTitle = null
    }

    suspend fun removeIdentity(siteId: String) {
        val normalizedSid = siteId.uppercase()
        deviceManager.removeIdentity(siteId)
        // Immediately delete campaigns for this site from DB to stop tracking
        campaignDao.deleteCampaignsByTag(siteId)
        activeSessions.remove(normalizedSid)
    }
}
