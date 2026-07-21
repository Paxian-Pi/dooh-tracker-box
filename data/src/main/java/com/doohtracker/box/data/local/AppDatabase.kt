package com.doohtracker.box.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CampaignEntity::class, TrackingLogEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun trackingLogDao(): TrackingLogDao
}
