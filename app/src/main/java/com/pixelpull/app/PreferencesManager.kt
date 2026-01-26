package com.pixelpull.app

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREFS_NAME = "wallpaper_prefs"
    private const val KEY_WALLPAPER_URL = "wallpaper_url"
    private const val KEY_SCHEDULE_HOUR = "schedule_hour"
    private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    
    private const val DEFAULT_URL = "https://lifecal-virid.vercel.app/months?height=2340&width=1080"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveWallpaperUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_WALLPAPER_URL, url).apply()
    }
    
    fun getWallpaperUrl(context: Context): String {
        return getPrefs(context).getString(KEY_WALLPAPER_URL, DEFAULT_URL) ?: DEFAULT_URL
    }
    
    fun saveScheduleTime(context: Context, hour: Int, minute: Int) {
        getPrefs(context).edit()
            .putInt(KEY_SCHEDULE_HOUR, hour)
            .putInt(KEY_SCHEDULE_MINUTE, minute)
            .putBoolean(KEY_SCHEDULE_ENABLED, true)
            .apply()
    }
    
    fun getScheduleHour(context: Context): Int {
        return getPrefs(context).getInt(KEY_SCHEDULE_HOUR, -1)
    }
    
    fun getScheduleMinute(context: Context): Int {
        return getPrefs(context).getInt(KEY_SCHEDULE_MINUTE, -1)
    }
    
    fun isScheduleEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)
    }
}
