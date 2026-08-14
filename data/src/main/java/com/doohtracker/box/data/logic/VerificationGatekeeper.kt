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
            signalMonitor.allSourceMetadata
        ) { activeCampaigns, isSourceActive, allMetadata ->
            // 1. Must have scheduled campaigns
            if (activeCampaigns.isEmpty()) return@combine false
            
            // 2. Source Monitor must be linked
            if (!isSourceActive || allMetadata.isEmpty()) return@combine false

            // 3. Content Verification Rule (Targeted Strictly - Active Only)
            // Only allow tracking if we have a match where SiteID in Pulse == SiteID in System AND window is open
            val allCampaigns = campaignRepository.allCampaigns.first()
            val hasTargetedMatch = allMetadata.any { (reportingSiteId, metadata) ->
                val reportedTitle = metadata.mediaTitle.lowercase()
                reportedTitle != "none" && allCampaigns.any { campaign ->
                    // MUST match both Site ID AND Title AND be active
                    campaign.campaignTag.equals(reportingSiteId, ignoreCase = true) && 
                    campaign.startTime <= currentTime && campaign.endTime >= currentTime && (
                        (campaign.sourceFilename?.lowercase()?.isNotEmpty() == true && reportedTitle.contains(campaign.sourceFilename.lowercase())) || 
                        reportedTitle.contains(campaign.name.lowercase())
                    )
                }
            }

            // Final Gate: Must be Linked + Confirmed Targeted Content Match
            hasTargetedMatch
        }
    }.distinctUntilChanged()
}
