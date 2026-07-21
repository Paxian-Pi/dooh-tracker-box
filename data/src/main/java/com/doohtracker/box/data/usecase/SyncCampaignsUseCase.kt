package com.doohtracker.box.data.usecase

import com.doohtracker.box.data.repository.CampaignRepository
import javax.inject.Inject

class SyncCampaignsUseCase @Inject constructor(
    private val repository: CampaignRepository
) {
    suspend operator fun invoke() {
        repository.syncCampaigns()
    }
}
