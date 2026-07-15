package com.ismartcoding.plain.tunnel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ismartcoding.plain.R
import com.ismartcoding.plain.lib.logcat.LogCat

object TunnelNotificationManager {
    private const val CHANNEL_ID = "ngrok_tunnel_channel"
    private const val NOTIFICATION_ID = 12345
    private var notificationManager: NotificationManager? = null

    fun initialize(context: Context) {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ngrok Tunnel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for ngrok tunnel status"
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showTunnelUrlNotification(context: Context, url: String) {
        try {
            LogCat.d("Showing tunnel URL notification: $url")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Ngrok Tunnel Ready")
                .setContentText(url)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Tunnel URL: $url"))
                .addAction(
                    0,
                    "Copy",
                    PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent(context, TunnelNotificationBroadcastReceiver::class.java).apply {
                            action = ACTION_COPY_URL
                            putExtra("url", url)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .addAction(
                    0,
                    "Share",
                    PendingIntent.getBroadcast(
                        context,
                        2,
                        Intent(context, TunnelNotificationBroadcastReceiver::class.java).apply {
                            action = ACTION_SHARE_URL
                            putExtra("url", url)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
            
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogCat.e("Error showing tunnel URL notification: ${e.message}", e)
        }
    }

    fun showTunnelErrorNotification(context: Context, error: String) {
        try {
            LogCat.d("Showing tunnel error notification: $error")
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Ngrok Tunnel Error")
                .setContentText(error)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Error: $error"))
                .build()
            
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogCat.e("Error showing tunnel error notification: ${e.message}", e)
        }
    }

    fun showTunnelConnectingNotification(context: Context) {
        try {
            LogCat.d("Showing tunnel connecting notification")
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Ngrok Tunnel")
                .setContentText("Connecting...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(0, 0, true) // Indeterminate progress
                .build()
            
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogCat.e("Error showing tunnel connecting notification: ${e.message}", e)
        }
    }

    fun cancelNotification() {
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            LogCat.e("Error canceling notification: ${e.message}", e)
        }
    }

    companion object {
        const val ACTION_COPY_URL = "com.ismartcoding.plain.COPY_TUNNEL_URL"
        const val ACTION_SHARE_URL = "com.ismartcoding.plain.SHARE_TUNNEL_URL"
    }
}
