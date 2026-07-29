package com.doohtracker.box.data.logic

import com.doohtracker.box.data.hardware.SignalMonitor
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
    private val signalMonitor: SignalMonitor,
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
            // Refined Active Rules:
            // 1. Must have scheduled campaigns
            val hasCampaigns = activeCampaigns.isNotEmpty()
            // 2. Source Monitor must be linked
            val isLinked = isSourceActive
            // 3. Must have external output (HDMI, etc)
            val hasExternalOutput = metadata?.activePorts != null && metadata.activePorts != "Internal/None"
            // 4. Must be actively transmitting media
            val isTransmitting = metadata?.isTransmitting == true

            hasCampaigns && isLinked && hasExternalOutput && isTransmitting
        }
    }.distinctUntilChanged()
}
