package com.vishal.vibeplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.vishal.vibeplayer.manager.PlayerManager

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")

        // 1. THIS IS THE FIX: Tell Android exactly what to do when lock screen buttons are tapped!
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { PlayerManager.play(this@MusicService) }
            override fun onPause() { PlayerManager.pause(this@MusicService) }
            override fun onSkipToNext() { PlayerManager.playNext(this@MusicService) }
            override fun onSkipToPrevious() { PlayerManager.playPrevious(this@MusicService) }
            override fun onSeekTo(pos: Long) { PlayerManager.seekTo(this@MusicService, pos.toInt()) }
            override fun onCustomAction(action: String?, extras: Bundle?) {
                if (action == "ACTION_FAVORITE") PlayerManager.toggleFavorite(this@MusicService)
            }
        })

        // Let the system know we are ready to receive Bluetooth & headphone clicks!
        mediaSession.isActive = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Fallback for older Android versions
        when (intent?.action) {
            "ACTION_PLAY" -> PlayerManager.play(this)
            "ACTION_PAUSE" -> PlayerManager.pause(this)
            "ACTION_NEXT" -> PlayerManager.playNext(this)
            "ACTION_PREV" -> PlayerManager.playPrevious(this)
            "ACTION_FAVORITE" -> PlayerManager.toggleFavorite(this)
        }
        showNotification()
        return START_STICKY
    }

    private fun showNotification() {
        val channelId = "VibePlayerChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Music Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val songTitle = PlayerManager.currentSong?.title ?: "Unknown Song"
        val songArtist = PlayerManager.currentSong?.artist ?: "Unknown Artist"
        val artBitmap = PlayerManager.currentSong?.art

        val isPlaying = PlayerManager.isPlaying
        val position = PlayerManager.mediaPlayer?.currentPosition?.toLong() ?: 0L
        val duration = PlayerManager.mediaPlayer?.duration?.toLong() ?: 0L

        // Check if the current song is in our Favorites Database
        val isFavorite = PlayerManager.favoriteSongs.contains(PlayerManager.currentSong?.path)
        val favIcon = if (isFavorite) android.R.drawable.star_on else android.R.drawable.star_off

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, songArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            .build()
        mediaSession.setMetadata(metadata)

        // 2. ADD FAVORITE ACTION TO PLAYBACK STATE (For Android 13+)
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, position, 1.0f)
            .addCustomAction("ACTION_FAVORITE", "Favorite", favIcon)
            .build()
        mediaSession.setPlaybackState(state)

        // Envelopes for older Android Notification Buttons
        val favIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_FAVORITE" }
        val favPending = PendingIntent.getService(this, 5, favIntent, PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PREV" }
        val prevPending = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val playIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PLAY" }
        val playPending = PendingIntent.getService(this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PAUSE" }
        val pausePending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_NEXT" }
        val nextPending = PendingIntent.getService(this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pausePending)
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playPending)
        }

        // 3. ASSEMBLE THE UI
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(songTitle)
            .setContentText(songArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(artBitmap)
            .addAction(favIcon, "Favorite", favPending)             // Button 0 (Favorite)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending) // Button 1
            .addAction(playPauseAction)                             // Button 2 (Play/Pause)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)         // Button 3
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                // Show Prev, Play/Pause, and Next in the compact (unexpanded) view
                .setShowActionsInCompactView(1, 2, 3)
            )
            .setOngoing(isPlaying)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
    }
}