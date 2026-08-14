package com.doohtracker.box.tracker.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.doohtracker.box.data.hardware.InternalMediaMonitor
import com.doohtracker.box.data.repository.CampaignRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class LocalMediaSessionService : NotificationListenerService() {

    @Inject
    lateinit var internalMonitor: InternalMediaMonitor

    @Inject
    lateinit var repository: CampaignRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSessionManager: MediaSessionManager? = null
    
    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            processMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // We could use this to detect status (HEALTHY vs NO_MEDIA)
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("LocalMonitorService", "Notification Listener Connected")
        
        val componentName = ComponentName(this, LocalMediaSessionService::class.java)
        mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
        
        // Initial check
        updateActiveControllers(mediaSessionManager?.getActiveSessions(componentName))
    }

    private fun updateActiveControllers(controllers: List<MediaController>?) {
        controllers?.forEach { controller ->
            controller.unregisterCallback(sessionCallback)
            controller.registerCallback(sessionCallback)
            // Process initial state
            processMetadata(controller.metadata)
        }
    }

    private fun processMetadata(metadata: MediaMetadata?) {
        if (!repository.deviceManager.isLocalMonitoringEnabled()) return

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) 
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "none"

        serviceScope.launch {
            val siteIds = repository.getActivationCodes()
            if (siteIds.isEmpty()) return@launch

            // For local mode, we broadcast the same title to all local Site IDs 
            // The HeartbeatServer logic will handle the actual campaign matching
            siteIds.forEach { sid ->
                internalMonitor.sendLocalPulse(
                    status = if (title != "none") "HEALTHY" else "NO_MEDIA",
                    isTransmitting = title != "none",
                    mediaTitle = title,
                    siteId = sid
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        serviceScope.cancel()
    }
}
