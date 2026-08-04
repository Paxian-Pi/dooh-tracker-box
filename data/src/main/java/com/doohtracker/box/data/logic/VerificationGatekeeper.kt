package com.doohtracker.box.data.logic

import com.doohtracker.box.data.hardware.SignalMonitor
import com.doohtracker.box.data.manager.DeviceManager
import com.doohtracker.box.data.repository.CampaignRepository
import com.doohtracker.box.data.time.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class VerificationGatekeeper @Inject constructor(
    private val campaignRepository: CampaignRepository,
    val signalMonitor: SignalMonitor,
    private val deviceManager: DeviceManager,
    private val timeProvider: TimeProvider
) {
    /**
     * Emits true when tracking is allowed:
     * 1. There is an active campaign according to the current time.
     * 2. The HDMI source is active (either via USB Capture or Network Heartbeat).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isTrackingAllowed: Flow<Boolean> = flow {
        while (true) {
            emit(timeProvider.getCurrentTimeMillis())
            delay(10000.milliseconds) 
        }
    }.flatMapLatest { currentTime ->
        combine(
            campaignRepository.getActiveCampaigns(currentTime),
            signalMonitor.isSourceActive,
            signalMonitor.sourceMetadata
        ) { activeCampaigns, isSourceActive, metadata ->
            // 1. Must have scheduled campaigns
            if (activeCampaigns.isEmpty()) return@combine false
            
            // 2. Source Monitor must be linked
            if (!isSourceActive || metadata == null) return@combine false

            // 3. Content Verification Rule (Strict Matching)
            // Verify that the billboard content exists in our SYSTEM (any downloaded campaign)
            val reportedTitle = metadata.mediaTitle.lowercase()
            val hasSystemMatch = campaignRepository.allCampaigns.first().any { campaign ->
                val fileName = campaign.sourceFilename?.lowercase() ?: ""
                val campaignName = campaign.name.lowercase()
                
                (fileName.isNotEmpty() && reportedTitle.contains(fileName)) || 
                reportedTitle.contains(campaignName)
            } && reportedTitle != "none"

            // Final Gate: Must be Linked + Trustworthy Content Match
            hasSystemMatch
        }
    }.distinctUntilChanged()
}
