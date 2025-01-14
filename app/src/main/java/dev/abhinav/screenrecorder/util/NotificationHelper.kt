package dev.abhinav.screenrecorder.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.abhinav.screenrecorder.MainActivity
import dev.abhinav.screenrecorder.R

object NotificationHelper {

    private const val CHANNEL_ID = "screen_record_channel"

    fun createNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun createNotificationChannel(context: Context) {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Screen Record Channel", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = context.getSystemService<NotificationManager>()
        notificationManager?.createNotificationChannel(serviceChannel)
    }
}