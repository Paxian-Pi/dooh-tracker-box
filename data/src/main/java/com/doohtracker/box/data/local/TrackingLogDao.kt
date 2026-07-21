package com.doohtracker.box.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingLogDao {
    @Insert
    suspend fun insertLog(log: TrackingLogEntity)

    @Query("SELECT * FROM tracking_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<TrackingLogEntity>

    @Update
    suspend fun updateLog(log: TrackingLogEntity)

    @Query("DELETE FROM tracking_logs WHERE isSynced = 1")
    suspend fun deleteSyncedLogs()

    @Query("SELECT * FROM tracking_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<TrackingLogEntity>>
}
