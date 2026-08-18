package com.doohtracker.box.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingLogDao {
    @Insert
    suspend fun insertLog(log: TrackingLogEntity): Long

    @Query("SELECT * FROM tracking_logs WHERE id = :id")
    suspend fun getLogById(id: Int): TrackingLogEntity?

    @Query("SELECT * FROM tracking_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<TrackingLogEntity>

    @Update
    suspend fun updateLog(log: TrackingLogEntity)

    @Query("DELETE FROM tracking_logs WHERE isSynced = 1 AND timestamp < :before")
    suspend fun deleteOldSyncedLogs(before: Long)

    @Query("SELECT * FROM tracking_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<TrackingLogEntity>>

    @Query("SELECT * FROM tracking_logs WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getLogsSinceFlow(since: Long): Flow<List<TrackingLogEntity>>

    @Query("SELECT * FROM tracking_logs WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getLogsSince(since: Long): List<TrackingLogEntity>

    @Query("DELETE FROM tracking_logs")
    suspend fun clearAll()
}
