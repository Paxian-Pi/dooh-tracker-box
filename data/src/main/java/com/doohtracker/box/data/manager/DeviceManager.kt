package com.doohtracker.box.data.manager

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DeviceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _identitiesFlow = MutableStateFlow<List<Identity>>(emptyList())
    val identitiesFlow: StateFlow<List<Identity>> = _identitiesFlow.asStateFlow()

    init {
        _identitiesFlow.value = getIdentities()
    }

    private val KEY_IDENTITIES = "activated_identities"
    private val KEY_LAST_SYNC_TIME = "last_sync_time"
    private val KEY_SOURCE_PREVIEW_ENABLED = "source_preview_enabled"
    private val KEY_AUTHORIZED_PORT = "authorized_monitoring_port"
    private val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
    private val KEY_LOCAL_MONITORING_ENABLED = "local_monitoring_enabled"

    private val _isDarkModeEnabledFlow = MutableStateFlow(isDarkModeEnabled())
    val isDarkModeEnabledFlow: StateFlow<Boolean> = _isDarkModeEnabledFlow.asStateFlow()

    private val _isLocalMonitoringEnabledFlow = MutableStateFlow(isLocalMonitoringEnabled())
    val isLocalMonitoringEnabledFlow: StateFlow<Boolean> = _isLocalMonitoringEnabledFlow.asStateFlow()

    fun isActivated(): Boolean = getIdentities().isNotEmpty()
    
    fun getAuthorizedPort(): String? = prefs.getString(KEY_AUTHORIZED_PORT, null)

    fun setAuthorizedPort(port: String?) {
        prefs.edit { putString(KEY_AUTHORIZED_PORT, port) }
    }

    fun isSourcePreviewEnabled(): Boolean = prefs.getBoolean(KEY_SOURCE_PREVIEW_ENABLED, false)

    fun setSourcePreviewEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOURCE_PREVIEW_ENABLED, enabled) }
    }

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE_ENABLED, true)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE_ENABLED, enabled) }
        _isDarkModeEnabledFlow.value = enabled
    }

    fun isLocalMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_MONITORING_ENABLED, false)

    fun setLocalMonitoringEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOCAL_MONITORING_ENABLED, enabled) }
        _isLocalMonitoringEnabledFlow.value = enabled
    }

    fun getIdentities(): List<Identity> {
        val json = prefs.getString(KEY_IDENTITIES, null) ?: return emptyList()
        val type = object : TypeToken<List<Identity>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addIdentity(identity: Identity) {
        val current = getIdentities().toMutableList()
        if (current.none { it.siteId == identity.siteId }) {
            current.add(identity)
            saveIdentities(current)
        }
    }

    fun removeIdentity(siteId: String) {
        val current = getIdentities().toMutableList()
        current.removeAll { it.siteId == siteId }
        saveIdentities(current)
    }

    private fun saveIdentities(identities: List<Identity>) {
        val json = gson.toJson(identities)
        prefs.edit { putString(KEY_IDENTITIES, json) }
        _identitiesFlow.value = identities
    }

    fun getInstallationNotes(): String = getIdentities().firstOrNull()?.location ?: ""
    fun getUsername(): String = getIdentities().firstOrNull()?.username ?: ""
    fun getActivationCode(): String = getIdentities().firstOrNull()?.siteId ?: ""

    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)

    fun updateLastSyncTime(time: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_TIME, time) }
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    }

    fun logout() {
        prefs.edit { clear() }
        _identitiesFlow.value = emptyList()
        _isDarkModeEnabledFlow.value = true
        _isLocalMonitoringEnabledFlow.value = false
    }
}
