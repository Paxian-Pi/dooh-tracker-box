package com.doohtracker.box.data.logic

import com.doohtracker.box.data.hardware.UsbConnectionMonitor
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
    private val usbConnectionMonitor: UsbConnectionMonitor,
    private val timeProvider: TimeProvider
) {
    /**
     * Emits true when tracking is allowed:
     * 1. There is an active campaign according to the current time.
     * 2. The HDMI Capture Card is detected via USB AND recognized by CameraManager.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isTrackingAllowed: Flow<Boolean> = flow {
        while (true) {
            emit(timeProvider.getCurrentTimeMillis())
            delay(10000.milliseconds) // Re-evaluate time-based campaigns every 10 seconds
        }
    }.flatMapLatest { currentTime ->
        campaignRepository.getActiveCampaigns(currentTime)
            .combine(usbConnectionMonitor.isUsbDeviceConnected) { activeCampaigns, isUsbConnected ->
                activeCampaigns.isNotEmpty() && isUsbConnected
            }
    }.distinctUntilChanged()
}
