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
    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    val recentLogs: Flow<List<TrackingLogEntity>> = trackingLogDao.getRecentLogs()
    val identities: Flow<List<Identity>> = deviceManager.identitiesFlow
    val isDarkModeEnabled: Flow<Boolean> = deviceManager.isDarkModeEnabledFlow
    val isLocalMonitoringEnabled: Flow<Boolean> = deviceManager.isLocalMonitoringEnabledFlow

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isDownloadingFiles = MutableStateFlow(false)
    val isDownloadingFiles: StateFlow<Boolean> = _isDownloadingFiles.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val isHubSyncing: Flow<Boolean> = allCampaigns.map { campaigns ->
        val now = timeProvider.getCurrentTimeMillis()
        val hasMissingActiveFiles = campaigns.any { 
            it.startTime <= now && it.endTime >= now && it.mediaUrl != null && it.localPath == null 
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

                identities.forEach { identity ->
                    val matchedResults = mediaResults.filter { it.campaignTag == identity.siteId && it.user == identity.username }
                    if (matchedResults.isNotEmpty()) {
                        val existingCampaigns = campaignDao.getAllCampaigns().first()
                        
                        // 1. Prepare and insert placeholders immediately so UI shows "Downloading"
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

                        // 2. Download files one-by-one and update DB in real-time
                        identityEntities.forEach { entity ->
                            if (entity.mediaUrl != null && entity.localPath == null) {
                                // Only set downloading flag if we actually have work to do
                                if (!_isDownloadingFiles.value) {
                                    _isDownloadingFiles.value = true
                                }
                                val file = mediaDownloader.downloadMedia(entity.mediaUrl, entity.id)
                                if (file != null) {
                                    // Update this specific campaign as "Done" immediately
                                    campaignDao.insertCampaigns(listOf(entity.copy(localPath = file.absolutePath)))
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
            // Keep campaigns that ended today for reporting purposes
            campaignDao.deleteExpiredCampaigns(startOfToday)
        } catch (e: Exception) {
            Log.e("Sync", "Error during synchronization", e)
        } finally {
            _isSyncing.value = false
            _isDownloadingFiles.value = false
        }
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
        val allCampaignsNow = campaignDao.getAllCampaigns().first()

        // 1. TARGETED MATCH (Active Only): Only match campaigns that belong to the reporting Site ID AND are within their active time window
        val matchedCampaign = allCampaignsNow.filter { 
            it.campaignTag.equals(metadata.siteId, ignoreCase = true) &&
            it.startTime <= currentTime && it.endTime >= currentTime // STRICT COMPLIANCE: Window check
        }.find { campaign ->
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val campaignName = campaign.name.lowercase()
            (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || reportedTitle.contains(campaignName)
        }?.takeIf { reportedTitle != "none" }

        val siteId = matchedCampaign?.campaignTag ?: "GLOBAL"
        val newCampaignId = matchedCampaign?.id
        val currentSession = activeSessions[siteId]

        // 1b. RECOGNITION TRIGGER: If unrecognized title detected, attempt immediate sync to fetch new reference
        if (matchedCampaign == null && reportedTitle != "none" && reportedTitle != lastSyncAttemptedTitle) {
            lastSyncAttemptedTitle = reportedTitle
            Log.d("Tracking", "Unrecognized title detected: $reportedTitle. Attempting immediate reference sync.")
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
                    if (durationSeconds >= 2L) { 
                        Log.d("Tracking", "Finalizing hub session for ${finishedCampaign.name}")
                        sendTrackingPulse(finishedCampaign, currentTime, locationString, durationSeconds, true)
                    }
                }
            }

            // B. Start new session for this client
            if (newCampaignId != null) {
                activeSessions[siteId] = PlaybackSession(newCampaignId, currentTime)
                Log.d("Tracking", "Started hub session for ${matchedCampaign?.name} on site $siteId")
                campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
            } else {
                activeSessions.remove(siteId)
            }
        } else if (newCampaignId != null) {
            // C. Maintain current session
            campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
        }

        lastPulseTime = currentTime
    }

    suspend fun trackCurrentCampaigns(currentTime: Long, locationString: String) {
        val activeCampaigns = campaignDao.getActiveCampaigns(currentTime).first()
        val allMetadataMap = signalMonitor.allSourceMetadata.first()
        
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
            val popResponse = api.sendProofOfPlay(popRequest)
            if (popResponse.isSuccessful) {
                trackingLogDao.insertLog(TrackingLogEntity(campaignId = campaign.id, timestamp = currentTime, isSynced = true))
                campaignDao.incrementSyncedCount(campaign.id)
                
                // Fetch updated campaign to get new count for hourly sync
                val updatedCampaign = campaignDao.getAllCampaigns().first().find { it.id == campaign.id } ?: campaign
                
                // 2. Send Hourly Track at the same time (syncs cumulative count)
                val hourlyRequest = TrackHourlyRequest(
                    user = updatedCampaign.user,
                    campaign = updatedCampaign.campaign,
                    campaignTag = updatedCampaign.campaignTag,
                    hours = hourFormat.format(now),
                    date = dateFormat.format(now),
                    count = updatedCampaign.syncedCount,
                    weekDay = weekDayNum,
                    location = location,
                    uniqueID = updatedCampaign.id
                )
                
                api.trackHourly(hourlyRequest)
                campaignDao.updateLastHourlyTrackedAt(updatedCampaign.id, currentTime)
            } else {
                trackingLogDao.insertLog(TrackingLogEntity(campaignId = campaign.id, timestamp = currentTime, isSynced = false))
            }
        } catch (e: Exception) {
            Log.e("Tracking", "Tracking pulse failed for ${campaign.id}", e)
            trackingLogDao.insertLog(TrackingLogEntity(campaignId = campaign.id, timestamp = currentTime, isSynced = false))
        }
    }

    suspend fun syncPendingLogs() {
        val unsyncedLogs = trackingLogDao.getUnsyncedLogs()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

        unsyncedLogs.forEach { log ->
            val campaign = campaignDao.getAllCampaigns().first().find { it.id == log.campaignId }
            if (campaign != null) {
                val date = Date(log.timestamp)
                val request = SendProofOfPlayRequest(
                    user = campaign.user,
                    campaign = campaign.campaign,
                    campaignTag = campaign.campaignTag,
                    time = timeFormat.format(date),
                    date = dateFormat.format(date),
                    day = dayFormat.format(date),
                    comment = "Delayed PoP sync",
                    location = deviceManager.getInstallationNotes(),
                    uniqueID = campaign.id,
                    durationCount = 0, // Fallback
                    confidence = 1.0f
                )

                try {
                    val response = api.sendProofOfPlay(request)
                    if (response.isSuccessful) {
                        trackingLogDao.updateLog(log.copy(isSynced = true))
                        campaignDao.incrementSyncedCount(campaign.id)
                    }
                } catch (_: Exception) {}
            }
        }
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        trackingLogDao.deleteOldSyncedLogs(oneDayAgo)
    }

    suspend fun clearData() {
        deviceManager.logout()
        campaignDao.clearAll()
        trackingLogDao.clearAll()
        mediaDownloader.clearMedia()
    }

    suspend fun removeIdentity(siteId: String) {
        deviceManager.removeIdentity(siteId)
        // Immediately delete campaigns for this site from DB to stop tracking
        campaignDao.deleteCampaignsByTag(siteId)
        activeSessions.remove(siteId)
    }
}
