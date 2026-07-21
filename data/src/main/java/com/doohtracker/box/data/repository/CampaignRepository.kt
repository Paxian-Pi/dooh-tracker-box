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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampaignRepository @Inject constructor(
    private val api: CampaignApi,
    private val campaignDao: CampaignDao,
    private val trackingLogDao: TrackingLogDao,
    private val deviceManager: DeviceManager
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
            
            // Requirement 1: Match code with campaignTag and user with username
            val isValid = results.any { it.campaignTag == code && it.user == user }

            if (isValid) {
                deviceManager.saveActivationInfo(user, notes)
                deviceManager.updateLastSyncTime(System.currentTimeMillis())
                // Store the matched campaigns, resetting counts on first activation
                campaignDao.insertCampaigns(results.map { it.toEntity() })
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

    suspend fun syncCampaigns() {
        try {
            Log.d("Sync", "Starting campaign synchronization...")
            // First try getMedia as requested
            val mediaResponse = api.getMedia()
            if (mediaResponse.isSuccessful) {
                deviceManager.updateLastSyncTime(System.currentTimeMillis())
                val mediaResults = mediaResponse.body()?.results ?: emptyList()
                Log.d("Sync", "getMedia successful. Found ${mediaResults.size} campaigns.")
                if (mediaResults.isNotEmpty()) {
                    val existingCampaigns = campaignDao.getAllCampaigns().first()
                    val entities = mediaResults.map { dto ->
                        val entity = dto.toEntity()
                        val existing = existingCampaigns.find { it.id == entity.id }
                        if (existing != null) {
                            entity.copy(syncedCount = existing.syncedCount)
                        } else {
                            entity
                        }
                    }
                    campaignDao.insertCampaigns(entities)
                }
            }
            campaignDao.deleteExpiredCampaigns(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("Sync", "Error during synchronization", e)
        }
    }

    suspend fun trackCurrentCampaigns(currentTime: Long, locationString: String) {
        val activeCampaigns = campaignDao.getActiveCampaigns(currentTime).first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val now = Date(currentTime)
        val calendar = Calendar.getInstance().apply { time = now }
        val currentHourStr = hourFormat.format(now)

        // Convert to numeric weekday (1 = Monday, ..., 7 = Sunday) as per requirement
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
            // 1. Regular Minute-by-Minute Proof of Play
            val popRequest = SendProofOfPlayRequest(
                user = campaign.user,
                campaign = campaign.campaign,
                campaignTag = campaign.campaignTag,
                time = timeFormat.format(now),
                date = dateFormat.format(now),
                day = dayFormat.format(now),
                comment = "Auto-tracked from Android Box",
                location = locationString.ifBlank { deviceManager.getInstallationNotes() },
                uniqueID = campaign.id,
                durationCount = 60,
                confidence = 1.0f
            )

            Log.d("Tracking", "Sending Proof of Play payload: $popRequest")

            val log = TrackingLogEntity(campaignId = campaign.id, timestamp = currentTime)

            try {
                val response = api.sendProofOfPlay(popRequest)
                if (response.isSuccessful) {
                    trackingLogDao.insertLog(log.copy(isSynced = true))
                    campaignDao.incrementSyncedCount(campaign.id)
                } else {
                    trackingLogDao.insertLog(log.copy(isSynced = false))
                }
            } catch (_: Exception) {
                trackingLogDao.insertLog(log.copy(isSynced = false))
            }

            // 2. Hourly Tracking (Triggered once per hour)
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
                    count = campaign.syncedCount + 1, // Current ping included
                    weekDay = weekDayNum,
                    location = locationString.ifBlank { deviceManager.getInstallationNotes() },
                    uniqueID = campaign.id
                )

                Log.d("Tracking", "Sending Hourly Track payload: $hourlyRequest")

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

    suspend fun syncPendingLogs() {
        val unsyncedLogs = trackingLogDao.getUnsyncedLogs()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

        unsyncedLogs.forEach { log ->
            // Re-fetch campaign data to get details
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
                    comment = "Delayed sync from local storage",
                    location = deviceManager.getInstallationNotes(),
                    uniqueID = campaign.id,
                    durationCount = 60,
                    confidence = 1.0f
                )

                Log.d("Tracking", "Syncing pending Proof of Play payload: $request")

                try {
                    val response = api.sendProofOfPlay(request)
                    if (response.isSuccessful) {
                        trackingLogDao.updateLog(log.copy(isSynced = true))
                        campaignDao.incrementSyncedCount(campaign.id)
                    }
                } catch (_: Exception) {
                    // Still offline
                }
            }
        }
        trackingLogDao.deleteSyncedLogs()
    }
}
