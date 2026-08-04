package com.doohtracker.box.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE startTime <= :currentTime AND endTime >= :currentTime")
    fun getActiveCampaigns(currentTime: Long): Flow<List<CampaignEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaigns(campaigns: List<CampaignEntity>)

    @Query("DELETE FROM campaigns WHERE endTime < :currentTime")
    suspend fun deleteExpiredCampaigns(currentTime: Long)

    @Query("DELETE FROM campaigns")
    suspend fun clearAll()

    @Query("UPDATE campaigns SET syncedCount = syncedCount + 1 WHERE id = :campaignId")
    suspend fun incrementSyncedCount(campaignId: String)

    @Query("UPDATE campaigns SET lastHourlyTrackedAt = :time WHERE id = :campaignId")
    suspend fun updateLastHourlyTrackedAt(campaignId: String, time: Long)

    @Query("UPDATE campaigns SET lastSeenOnBillboardAt = :time WHERE id = :campaignId")
    suspend fun updateLastSeenOnBillboardAt(campaignId: String, time: Long)
}
