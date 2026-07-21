package com.doohtracker.box.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.doohtracker.box.data.repository.CampaignRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CampaignSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CampaignRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.syncCampaigns()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
