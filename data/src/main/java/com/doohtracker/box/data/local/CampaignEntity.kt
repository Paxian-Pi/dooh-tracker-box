package com.doohtracker.box.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey
    val id: String, // uniqueID
    val name: String, // title
    val campaign: String,
    val campaignTag: String,
    val user: String,
    val startTime: Long,
    val endTime: Long,
    val mediaUrl: String?,
    val syncedCount: Int = 0,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val lastHourlyTrackedAt: Long = 0L
)
