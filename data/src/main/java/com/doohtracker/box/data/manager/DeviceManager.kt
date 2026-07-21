package com.doohtracker.box.data.manager

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class DeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    private val KEY_IS_ACTIVATED = "is_activated"
    private val KEY_INSTALLATION_NOTES = "installation_notes"
    private val KEY_USERNAME = "username"
    private val KEY_LAST_SYNC_TIME = "last_sync_time"

    fun isActivated(): Boolean = prefs.getBoolean(KEY_IS_ACTIVATED, false)

    fun getInstallationNotes(): String = prefs.getString(KEY_INSTALLATION_NOTES, "") ?: ""
    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)

    fun updateLastSyncTime(time: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_TIME, time) }
    }

    fun saveActivationInfo(username: String, notes: String) {
        prefs.edit {
            putBoolean(KEY_IS_ACTIVATED, true)
            putString(KEY_USERNAME, username)
            putString(KEY_INSTALLATION_NOTES, notes)
        }
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    }
}
