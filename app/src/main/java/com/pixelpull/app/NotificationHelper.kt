package com.pixelpull.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    
    private const val CHANNEL_ID = "wallpaper_updates"
    private const val CHANNEL_NAME = "Wallpaper Updates"
    private const val NOTIFICATION_ID_ERROR = 1001
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for wallpaper update status"
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showErrorNotification(context: Context, errorMessage: String) {
        createNotificationChannel(context)
        
        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Determine error title based on error message
        val title = when {
            errorMessage.contains("network", ignoreCase = true) || 
            errorMessage.contains("internet", ignoreCase = true) ||
            errorMessage.contains("connection", ignoreCase = true) -> "No Internet Connection"
            errorMessage.contains("download", ignoreCase = true) ||
            errorMessage.contains("Failed to download", ignoreCase = true) -> "Download Failed"
            errorMessage.contains("decode", ignoreCase = true) -> "Invalid Image"
            else -> "Wallpaper Update Failed"
        }
        
        // Create notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Couldn't update wallpaper. Tap to open app.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Failed to update your wallpaper automatically. Please check your internet connection and try again."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    fun showSuccessNotification(context: Context) {
        createNotificationChannel(context)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Wallpaper Updated")
            .setContentText("Your wallpaper was updated successfully")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
}
