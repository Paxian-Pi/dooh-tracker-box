package com.doohtracker.box.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_logs")
data class TrackingLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val campaignId: String,
    val timestamp: Long,
    val isSynced: Boolean = false
)
