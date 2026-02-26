package com.vishal.vibeplayer.manager

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song

object PlayerManager {

    var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
    var currentSong: Song? = null
    var onPlayerStateChanged: (() -> Unit)? = null

    fun initializeAndPlay(context: Context, song: Song? = null, rawSongId: Int = R.raw.sample_song) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, rawSongId)

        // --- 1. THE MAGIC EXTRACTOR ---
        val retriever = MediaMetadataRetriever()
        val uri = Uri.parse("android.resource://" + context.packageName + "/" + rawSongId)

        try {
            retriever.setDataSource(context, uri)

            // Extract the real text embedded in the file
            val realTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: song?.title ?: "Unknown Title"
            val realArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: song?.artist ?: "Unknown Artist"

            // Extract the Image bytes and convert them into a Bitmap
            val artBytes = retriever.embeddedPicture
            val realArt = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null

            // Save the real extracted data as our Current Song
            currentSong = Song(realTitle, realArtist, song?.duration ?: "0:00", realArt)

        } catch (e: Exception) {
            currentSong = song // Fallback if parsing fails
        } finally {
            retriever.release() // Always clean up the scanner!
        }
        // ------------------------------

        mediaPlayer?.start()
        isPlaying = true
        onPlayerStateChanged?.invoke()
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