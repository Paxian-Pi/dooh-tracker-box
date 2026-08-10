package com.doohtracker.box.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.doohtracker.box.data.logic.VerificationGatekeeper
import com.doohtracker.box.data.repository.CampaignRepository
import com.doohtracker.box.data.location.LocationMonitor
import com.doohtracker.box.data.time.TimeProvider
import com.doohtracker.box.tracker.player.PlaybackManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@AndroidEntryPoint
class TrackerService : Service() {

    @Inject
    lateinit var gatekeeper: VerificationGatekeeper

    @Inject
    lateinit var campaignRepository: CampaignRepository

    @Inject
    lateinit var locationMonitor: LocationMonitor

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var timeProvider: TimeProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        observeTrackingLogic()
        startPendingLogsSyncLoop()
        startPeriodicCampaignSync()
    }

    private fun observeTrackingLogic() {
        serviceScope.launch {
            // High-frequency Pulse Listener (Every 2 seconds via network)
            gatekeeper.signalMonitor.allSourceMetadata.collect { allMetadata ->
                val location = locationMonitor.currentLocation.first()
                val locationString = if (location != null) {
                    "${location.latitude}, ${location.longitude}"
                } else ""

                allMetadata.values.forEach { metadata ->
                    campaignRepository.processBillboardPulse(
                        currentTime = timeProvider.getCurrentTimeMillis(),
                        metadata = metadata,
                        locationString = locationString
                    )
                }
            }
        }

        serviceScope.launch {
            // General status observer for UI/Start-Stop
            gatekeeper.isTrackingAllowed.collect { allowed ->
                if (allowed) {
                    startCompliancePoller()
                } else {
                    stopCompliancePoller()
                }
            }
        }
    }

    private fun startCompliancePoller() {
        if (trackingJob?.isActive == true) return
        trackingJob = serviceScope.launch {
            while (isActive) {
                val location = locationMonitor.currentLocation.first()
                val locationString = if (location != null) {
                    "${location.latitude}, ${location.longitude}"
                } else ""

                // This now only handles background compliance (❌ logs for missing ads)
                campaignRepository.trackCurrentCampaigns(
                    currentTime = timeProvider.getCurrentTimeMillis(),
                    locationString = locationString
                )
                delay(60000.milliseconds) 
            }
        }
    }

    private fun startPeriodicCampaignSync() {
        serviceScope.launch {
            while (isActive) {
                // Periodically download campaigns from server to catch updates without restart
                campaignRepository.syncCampaigns()
                delay(60000.milliseconds) // Check for new campaigns every minute
            }
        }
    }

    private fun stopCompliancePoller() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun startPendingLogsSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                campaignRepository.syncPendingLogs()
                delay(300000.milliseconds) // Try to sync pending logs every 5 minutes
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "tracker_service"
        val channelName = "Campaign Tracker"
        val manager = getSystemService(NotificationManager::class.java)
        
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("DOOH Tracker Active")
            .setContentText("Monitoring campaigns and HDMI signal...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
