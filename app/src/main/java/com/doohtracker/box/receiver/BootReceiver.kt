package com.doohtracker.box.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.doohtracker.box.MainActivity
import com.doohtracker.box.tracker.service.TrackerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // 1. Start the Tracking Service (Foreground)
            val serviceIntent = Intent(context, TrackerService::class.java)
            context.startForegroundService(serviceIntent)

            // 2. Start the Main Activity (Dashboard)
            // Note: Since Android 10, there are restrictions on starting activities from the background.
            // On a dedicated TV Box, this often works or requires the "Display over other apps" permission.
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(activityIntent)
        }
    }
}
