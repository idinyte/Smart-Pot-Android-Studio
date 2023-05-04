package com.example.smartpot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

private const val NOTIFICATION_CHANNEL_ID = "my_channel_id"
private const val NOTIFICATION_ID = 1

class MyNotificationManager{
        // Function to show a notification
        fun showNotification(context: Context, title: String, message: String, drawable: Int) {

            // Create the notification channel
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "My Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                channel.description = "My Channel Description"
                channel.enableLights(true)
                channel.lightColor = ContextCompat.getColor(context, R.color.md_theme_light_primary)
                channel.enableVibration(true)
                notificationManager.createNotificationChannel(channel)
            }

            // Build the notification
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(drawable)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            // Show the notification
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
}
