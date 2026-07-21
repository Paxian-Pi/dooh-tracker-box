package com.doohtracker.box.util

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemActions @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun rebootDevice() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for rooted devices or shell-enabled TV boxes
            try {
                Runtime.getRuntime().exec("reboot")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
