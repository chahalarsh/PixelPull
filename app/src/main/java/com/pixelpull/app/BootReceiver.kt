package com.pixelpull.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule alarm after device reboot
            val hour = PreferencesManager.getScheduleHour(context)
            val minute = PreferencesManager.getScheduleMinute(context)
            
            if (hour >= 0 && minute >= 0 && PreferencesManager.isScheduleEnabled(context)) {
                AlarmScheduler.scheduleWallpaperUpdate(context, hour, minute)
            }
        }
    }
}
