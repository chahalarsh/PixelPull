package com.pixelpull.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class WallpaperUpdateReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val url = PreferencesManager.getWallpaperUrl(context)
        
        // Download and set wallpaper in background
        CoroutineScope(Dispatchers.IO).launch {
            val result = WallpaperService.downloadAndSetWallpaper(context, url)
            
            // Show toast on main thread
            CoroutineScope(Dispatchers.Main).launch {
                if (result.isSuccess) {
                    Toast.makeText(
                        context,
                        "Wallpaper updated automatically",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Failed to update wallpaper: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        // Reschedule for tomorrow
        scheduleNextUpdate(context)
    }
    
    private fun scheduleNextUpdate(context: Context) {
        val hour = PreferencesManager.getScheduleHour(context)
        val minute = PreferencesManager.getScheduleMinute(context)
        
        if (hour >= 0 && minute >= 0) {
            AlarmScheduler.scheduleWallpaperUpdate(context, hour, minute)
        }
    }
}

object AlarmScheduler {
    
    fun scheduleWallpaperUpdate(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WallpaperUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Calculate next trigger time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If time has passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        // Schedule exact alarm
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact alarm permission not granted
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
    
    fun cancelWallpaperUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WallpaperUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
