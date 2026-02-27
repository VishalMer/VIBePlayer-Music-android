package com.vishal.vibeplayer.manager

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import com.vishal.vibeplayer.model.Song
import com.vishal.vibeplayer.service.MusicService

object PlayerManager {

    var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
    var currentSong: Song? = null
    var onPlayerStateChanged: (() -> Unit)? = null

    // 1. We added "context: Context" to the parameters here!
    fun initializeAndPlay(context: Context, song: Song) {
        currentSong = song

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()

        try {
            // Tell the player the exact file path on the phone
            mediaPlayer?.setDataSource(song.path)
            mediaPlayer?.prepare()

            // Scan the real file for its embedded Album Art
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(song.path)

            val artBytes = retriever.embeddedPicture
            val realArt = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null

            // Update the song with the real art
            currentSong = song.copy(art = realArt)
            retriever.release()

            // Play it!
            mediaPlayer?.start()
            isPlaying = true
            onPlayerStateChanged?.invoke()

            // 2. THE NEW MAGIC: Launch the background service so the app survives!
            val intent = Intent(context, MusicService::class.java)
            ContextCompat.startForegroundService(context, intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        mediaPlayer?.start()
        isPlaying = true
        onPlayerStateChanged?.invoke()
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        onPlayerStateChanged?.invoke()
    }
}