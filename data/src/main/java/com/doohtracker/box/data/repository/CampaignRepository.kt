package com.doohtracker.box.data.repository

import com.doohtracker.box.data.local.CampaignDao
import com.doohtracker.box.data.local.CampaignEntity
import com.doohtracker.box.data.local.TrackingLogDao
import com.doohtracker.box.data.local.TrackingLogEntity
import com.doohtracker.box.data.manager.DeviceManager
import com.doohtracker.box.data.model.SendProofOfPlayRequest
import com.doohtracker.box.data.model.TrackHourlyRequest
import com.doohtracker.box.data.model.toEntity
import com.doohtracker.box.data.remote.CampaignApi
import com.doohtracker.box.data.remote.MediaDownloader
import com.doohtracker.box.data.time.TimeProvider
import com.doohtracker.box.data.util.baseUrl
import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.data.hardware.SourceMetadata
import com.doohtracker.box.data.hardware.UsbConnectionMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampaignRepository @Inject constructor(
    private val api: CampaignApi,
    private val campaignDao: CampaignDao,
    private val trackingLogDao: TrackingLogDao,
    private val deviceManager: DeviceManager,
    private val signalMonitor: SignalMonitor,
    private val mediaDownloader: MediaDownloader,
    private val timeProvider: TimeProvider
) {
    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    val recentLogs: Flow<List<TrackingLogEntity>> = trackingLogDao.getRecentLogs()

    // Session Tracking variables
    private var activeSessionCampaignId: String? = null
    private var activeSessionStartTime: Long = 0L
    private var lastPulseTime: Long = 0L

    fun isDeviceActivated(): Boolean = deviceManager.isActivated()
    fun getActivationCode(): String = deviceManager.getActivationCode()
    fun getInstallationNotes(): String = deviceManager.getInstallationNotes()
    fun getLastSyncTime(): Long = deviceManager.getLastSyncTime()
    
    fun isHdmiStreamEnabled(): Boolean = deviceManager.isHdmiStreamEnabled()
    fun setHdmiStreamEnabled(enabled: Boolean) = deviceManager.setHdmiStreamEnabled(enabled)

    suspend fun activateDevice(code: String, user: String, notes: String): Result<Unit> {
        return try {
            val mediaResponse = api.getMedia()
            if (!mediaResponse.isSuccessful) {
                return Result.failure(Exception("Could not reach backend for validation"))
            }

            val results = mediaResponse.body()?.results ?: emptyList()
            
            // Filter results where campaignTag matches activationCode AND user matches username
            val matchedCampaigns = results.filter { it.campaignTag == code && it.user == user }

            if (matchedCampaigns.isNotEmpty()) {
                deviceManager.saveActivationInfo(user, code, notes)
                deviceManager.updateLastSyncTime(timeProvider.getCurrentTimeMillis())
                
                // Immediately insert matched campaign metadata
                val entities = matchedCampaigns.map { it.toEntity().normalizeUrl() }
                campaignDao.insertCampaigns(entities)

                // Trigger background media download without blocking the UI
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
        try {
            val mediaResponse = api.getMedia()
            if (mediaResponse.isSuccessful) {
                deviceManager.updateLastSyncTime(timeProvider.getCurrentTimeMillis())
                val mediaResults = mediaResponse.body()?.results ?: emptyList()
                
                // Filter by stored credentials
                val code = deviceManager.getActivationCode()
                val user = deviceManager.getUsername()
                val matchedResults = mediaResults.filter { it.campaignTag == code && it.user == user }

                if (matchedResults.isNotEmpty()) {
                    val existingCampaigns = campaignDao.getAllCampaigns().first()
                    val entities = matchedResults.map { dto ->
                        val entity = dto.toEntity().normalizeUrl()
                        
                        val sourceFilename = entity.mediaUrl?.substringAfterLast("/")?.substringBefore("?")
                        
                        // Download media if not already present
                        val localFile = entity.mediaUrl?.let { url ->
                            mediaDownloader.downloadMedia(url, entity.id)
                        }
                        
                        val existing = existingCampaigns.find { it.id == entity.id }
                        if (existing != null) {
                            entity.copy(
                                syncedCount = existing.syncedCount,
                                sourceFilename = sourceFilename ?: existing.sourceFilename,
                                localPath = localFile?.absolutePath ?: existing.localPath
                            )
                        } else {
                            entity.copy(
                                sourceFilename = sourceFilename,
                                localPath = localFile?.absolutePath
                            )
                        }
                    }
                    campaignDao.insertCampaigns(entities)
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

        // 1. Find the campaign matching current physical output
        val matchedCampaign = allCampaignsNow.find { campaign ->
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val campaignName = campaign.name.lowercase()
            (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || reportedTitle.contains(campaignName)
        }?.takeIf { reportedTitle != "none" }

        val newCampaignId = matchedCampaign?.id

        // 2. Logic: Handle Session Changes OR Hourly Boundary Crossings
        val currentHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(currentTime))
        val sessionStartHour = if (activeSessionStartTime > 0) {
            SimpleDateFormat("HH", Locale.getDefault()).format(Date(activeSessionStartTime))
        } else currentHour

        val hasContentChanged = newCampaignId != activeSessionCampaignId
        val hasHourChanged = currentHour != sessionStartHour

        if (hasContentChanged || hasHourChanged) {
            // A. Finalize previous session
            if (activeSessionCampaignId != null) {
                val finishedCampaign = allCampaignsNow.find { it.id == activeSessionCampaignId }
                if (finishedCampaign != null) {
                    val durationSeconds = (currentTime - activeSessionStartTime) / 1000L
                    if (durationSeconds >= 2) { 
                        Log.d("Tracking", "Finalizing session (Hour change or Content change) for ${finishedCampaign.name}")
                        sendTrackingPulse(finishedCampaign, currentTime, locationString, durationSeconds, true)
                    }
                }
            }

            // B. Start new session (or restart for same ad in new hour)
            activeSessionCampaignId = newCampaignId
            activeSessionStartTime = currentTime
            
            if (newCampaignId != null) {
                Log.d("Tracking", "Started session for ${matchedCampaign?.name}")
                campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
            }
        } else if (newCampaignId != null) {
            // C. Maintain current session
            campaignDao.updateLastSeenOnBillboardAt(newCampaignId, currentTime)
        }

        lastPulseTime = currentTime
    }

    suspend fun trackCurrentCampaigns(currentTime: Long, locationString: String) {
        val activeCampaigns = campaignDao.getActiveCampaigns(currentTime).first()
        val metadata = signalMonitor.sourceMetadata.first()
        val reportedTitle = metadata?.mediaTitle?.lowercase() ?: ""

        val rotationGracePeriod = 10 * 60 * 1000L 
        val suspensionThreshold = 20 * 60 * 1000L

        // Handle Heartbeat Timeout (Billboard player closed suddenly)
        if (currentTime - lastPulseTime > 10000 && activeSessionCampaignId != null) {
            Log.d("Tracking", "Billboard signal lost. Closing active session.")
            processBillboardPulse(currentTime, SourceMetadata(mediaTitle = "none"), locationString)
        }

        activeCampaigns.forEach { campaign ->
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val campaignName = campaign.name.lowercase()
            val isCurrentMatch = (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                                 reportedTitle.contains(campaignName)

            // Update last seen to keep it from being suspended,
            // but we DO NOT send a tracking pulse here. 
            // Pulses are sent only when the session ends in processBillboardPulse().
            if (isCurrentMatch && reportedTitle != "none") {
                campaignDao.updateLastSeenOnBillboardAt(campaign.id, currentTime)
            }

            // Only generate ❌ logs here for ads that are NOT currently playing
            if (!isCurrentMatch || reportedTitle == "none") {
                val timeSinceSeen = currentTime - campaign.lastSeenOnBillboardAt
                val isMissingFromRotation = campaign.lastSeenOnBillboardAt != 0L && timeSinceSeen > rotationGracePeriod
                val isNewAndNotShowing = campaign.lastSeenOnBillboardAt == 0L && timeSinceSeen > rotationGracePeriod

                if ((isMissingFromRotation || isNewAndNotShowing) && timeSinceSeen < suspensionThreshold) {
                    if (reportedTitle != "none") {
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

    private suspend fun sendTrackingPulse(
        campaign: CampaignEntity,
        currentTime: Long,
        locationString: String,
        durationSeconds: Long,
        bypassContentCheck: Boolean = false
    ) {
        if (!bypassContentCheck) {
            // Safety valve for manual calls: Ensure content actually matches billboard
            val metadata = signalMonitor.sourceMetadata.first()
            val reportedTitle = metadata?.mediaTitle?.lowercase() ?: ""
            val fileName = campaign.sourceFilename?.lowercase() ?: ""
            val isMatch = (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                         reportedTitle.contains(campaign.name.lowercase())
            
            if (!isMatch || reportedTitle == "none") {
                Log.d("Tracking", "Pulse rejected for ${campaign.name}: Content Mismatch")
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

    suspend fun logout() {
        deviceManager.logout()
        campaignDao.clearAll()
        trackingLogDao.clearAll()
        mediaDownloader.clearMedia()
    }
}
