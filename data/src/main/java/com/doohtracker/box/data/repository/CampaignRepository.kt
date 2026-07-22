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
    private val usbMonitor: UsbConnectionMonitor,
    private val mediaDownloader: MediaDownloader,
    private val timeProvider: TimeProvider
) {
    val allCampaigns: Flow<List<CampaignEntity>> = campaignDao.getAllCampaigns()
    val recentLogs: Flow<List<TrackingLogEntity>> = trackingLogDao.getRecentLogs()

    fun isDeviceActivated(): Boolean = deviceManager.isActivated()
    fun getInstallationNotes(): String = deviceManager.getInstallationNotes()
    fun getLastSyncTime(): Long = deviceManager.getLastSyncTime()

    suspend fun activateDevice(code: String, user: String, notes: String): Result<Unit> {
        return try {
            val mediaResponse = api.getMedia()
            if (!mediaResponse.isSuccessful) {
                return Result.failure(Exception("Could not reach backend for validation"))
            }

            val results = mediaResponse.body()?.results ?: emptyList()
            val isValid = results.any { it.campaignTag == code && it.user == user }

            if (isValid) {
                deviceManager.saveActivationInfo(user, notes)
                deviceManager.updateLastSyncTime(timeProvider.getCurrentTimeMillis())
                
                // Immediately insert campaign metadata so we can land on dashboard
                val entities = results.map { it.toEntity().normalizeUrl() }
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
                if (mediaResults.isNotEmpty()) {
                    val existingCampaigns = campaignDao.getAllCampaigns().first()
                    val entities = mediaResults.map { dto ->
                        val entity = dto.toEntity().normalizeUrl()
                        
                        // Download media if not already present
                        val localFile = entity.mediaUrl?.let { url ->
                            mediaDownloader.downloadMedia(url, entity.id)
                        }
                        
                        val existing = existingCampaigns.find { it.id == entity.id }
                        if (existing != null) {
                            entity.copy(
                                syncedCount = existing.syncedCount,
                                localPath = localFile?.absolutePath ?: existing.localPath
                            )
                        } else {
                            entity.copy(localPath = localFile?.absolutePath)
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

    suspend fun trackCurrentCampaigns(currentTime: Long, locationString: String) {
        val activeCampaigns = campaignDao.getActiveCampaigns(currentTime).first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Date(currentTime)
        val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
        val calendar = Calendar.getInstance().apply { time = now }
        val currentHourStr = hourFormat.format(now)

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

        activeCampaigns.forEach { campaign ->
            val lastTrackedHour = if (campaign.lastHourlyTrackedAt > 0) {
                hourFormat.format(Date(campaign.lastHourlyTrackedAt))
            } else ""

            if (currentHourStr != lastTrackedHour) {
                val hourlyRequest = TrackHourlyRequest(
                    user = campaign.user,
                    campaign = campaign.campaign,
                    campaignTag = campaign.campaignTag,
                    hours = currentHourStr,
                    date = dateFormat.format(now),
                    count = campaign.syncedCount, 
                    weekDay = weekDayNum,
                    location = locationString.ifBlank { deviceManager.getInstallationNotes() },
                    uniqueID = campaign.id
                )

                try {
                    val response = api.trackHourly(hourlyRequest)
                    if (response.isSuccessful) {
                        campaignDao.updateLastHourlyTrackedAt(campaign.id, currentTime)
                    }
                } catch (_: Exception) {
                    Log.e("Tracking", "Hourly track failed for ${campaign.id}")
                }
            }
        }
    }

    suspend fun trackPlayback(campaignId: String, durationSeconds: Long) {
        // Gate check without circular dependency on VerificationGatekeeper
        val isHardwareActive = usbMonitor.isUsbDeviceConnected.first()
        if (!isHardwareActive) {
            Log.d("Tracking", "Suppressed PoP: HDMI Hardware Inactive")
            return
        }

        val campaigns = campaignDao.getAllCampaigns().first()
        val campaign = campaigns.find { it.id == campaignId } ?: return
        
        val currentTime = timeProvider.getCurrentTimeMillis()
        val now = Date(currentTime)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        
        val locationString = deviceManager.getInstallationNotes() 

        val request = SendProofOfPlayRequest(
            user = campaign.user,
            campaign = campaign.campaign,
            campaignTag = campaign.campaignTag,
            time = timeFormat.format(now),
            date = dateFormat.format(now),
            day = dayFormat.format(now),
            comment = "Playback-based tracking",
            location = locationString,
            uniqueID = campaign.id,
            durationCount = durationSeconds,
            confidence = 1.0f
        )

        Log.d("Tracking", "Sending Proof of Play: $request")

        val log = TrackingLogEntity(campaignId = campaign.id, timestamp = currentTime)
        try {
            val response = api.sendProofOfPlay(request)
            if (response.isSuccessful) {
                trackingLogDao.insertLog(log.copy(isSynced = true))
                campaignDao.incrementSyncedCount(campaign.id)
            } else {
                trackingLogDao.insertLog(log.copy(isSynced = false))
            }
        } catch (_: Exception) {
            trackingLogDao.insertLog(log.copy(isSynced = false))
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
}
