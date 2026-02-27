package com.vishal.vibeplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vishal.vibeplayer.manager.PlayerManager

class MusicService : Service() {

    // We don't need this for a simple music player, so we return null
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // This runs every time we tell the service to start!
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification()

        // START_STICKY tells Android: "If you accidentally kill me, restart me immediately!"
        return START_STICKY
    }

    private fun showNotification() {
        val channelId = "VibePlayerChannel"

        // 1. Android 8.0+ requires a "Notification Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW // Low = no annoying pop-up sound when the song changes
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 2. Grab the current song data from your Global State!
        val songTitle = PlayerManager.currentSong?.title ?: "Unknown Song"
        val songArtist = PlayerManager.currentSong?.artist ?: "Unknown Artist"

        // 3. Build the actual Notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(songTitle)
            .setContentText(songArtist)
            .setSmallIcon(android.R.drawable.ic_media_play) // Just a temporary icon for now
            .setOngoing(true) // Prevents the user from accidentally swiping the notification away!
            .build()

        // 4. Start the Foreground Service to keep the app alive!
        startForeground(1, notification)
    }
}