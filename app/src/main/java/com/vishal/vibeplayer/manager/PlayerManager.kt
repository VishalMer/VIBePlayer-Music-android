package com.vishal.vibeplayer.manager

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.vishal.vibeplayer.model.Song
import com.vishal.vibeplayer.service.MusicService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PlayerManager {

    var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
    var currentSong: Song? = null
    var onPlayerStateChanged: (() -> Unit)? = null
    var onMiniPlayerUpdate: (() -> Unit)? = null

    // --- TRUE SHUFFLE UPGRADE: Keep track of the original sequence ---
    var originalPlaylist: List<Song> = emptyList()
    var currentPlaylist: List<Song> = emptyList()
    var allSongs: List<Song> = emptyList()
    var currentIndex: Int = -1
    val favoriteSongs = mutableSetOf<String>()

    var isShuffleEnabled = false
    var isRepeatEnabled = false

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var resumeOnFocusGain = false

    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var isFavoritesLoaded = false
    var savedPlaybackPosition: Int = 0

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (resumeOnFocusGain) {
                    appContext?.let { play(it) }
                    resumeOnFocusGain = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS -> {
                if (isPlaying) {
                    resumeOnFocusGain = true
                    appContext?.let { pause(it) }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
        }
    }

    fun loadFavorites(context: Context) {
        if (isFavoritesLoaded) return
        val prefs = context.getSharedPreferences("VibePlayerPrefs", Context.MODE_PRIVATE)
        val savedFavs = prefs.getStringSet("FAVORITES", emptySet())
        if (savedFavs != null) {
            favoriteSongs.clear()
            favoriteSongs.addAll(savedFavs)
        }
        isFavoritesLoaded = true
    }

    private fun saveFavorites(context: Context) {
        val prefs = context.getSharedPreferences("VibePlayerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("FAVORITES", favoriteSongs.toSet()).apply()
    }

    fun startPlaying(context: Context, playlist: List<Song>, index: Int) {
        this.appContext = context.applicationContext
        loadFavorites(context)

        if (transitionWakeLock == null) {
            val powerManager = appContext?.getSystemService(Context.POWER_SERVICE) as PowerManager
            transitionWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibePlayer::TransitionLock")
        }
        transitionWakeLock?.acquire(3000)

        // ==========================================
        // --- THE BUG FIX: PREVENT INFINITE SHUFFLING ---
        // ==========================================
        // Check if the user is clicking a BRAND NEW playlist, or just hitting Next/Prev!
        val isSamePlaylist = (playlist == currentPlaylist)

        if (!isSamePlaylist) {
            // It's a brand new list! Save the original order.
            originalPlaylist = playlist.toList()
            currentPlaylist = playlist.toMutableList()
            currentIndex = index

            // INSTANT SHUFFLE: Only shuffle if it's a new list!
            if (isShuffleEnabled && currentPlaylist.size > 1) {
                val currentActiveSong = currentPlaylist[currentIndex]
                val remainingSongs = currentPlaylist.toMutableList()
                remainingSongs.removeAt(currentIndex)
                remainingSongs.shuffle() // Randomize the deck once

                val newPlaylist = mutableListOf(currentActiveSong)
                newPlaylist.addAll(remainingSongs)

                currentPlaylist = newPlaylist
                currentIndex = 0 // The clicked song is now at the top
            }
        } else {
            // It is the EXACT same playlist (User hit Next, Prev, or clicked a queue song).
            // Do NOT shuffle again! Just update the index so we move down the list normally.
            currentIndex = index
        }

        // Now grab the correct song using the updated index
        val song = currentPlaylist[currentIndex]
        currentSong = song.copy(art = null)

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            }
        } else {
            mediaPlayer?.reset()
        }

        try {
            mediaPlayer?.setDataSource(song.path)
            mediaPlayer?.prepare()

            if (savedPlaybackPosition > 0) {
                mediaPlayer?.seekTo(savedPlaybackPosition)
                savedPlaybackPosition = 0
            }

            mediaPlayer?.setOnCompletionListener {
                appContext?.let { ctx -> playNext(ctx) }
            }

            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focusGranted = requestAudioFocus()

            if (focusGranted) {
                mediaPlayer?.start()
                isPlaying = true
                onPlayerStateChanged?.invoke()
                onMiniPlayerUpdate?.invoke()

                val intent = Intent(context, MusicService::class.java)
                ContextCompat.startForegroundService(context, intent)

                Thread {
                    if (!song.isOnline) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(song.path)
                            val artBytes = retriever.embeddedPicture
                            val realArt = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null
                            retriever.release()

                            if (currentSong?.path == song.path) {
                                currentSong = currentSong?.copy(art = realArt)
                                Handler(Looper.getMainLooper()).post {
                                    onPlayerStateChanged?.invoke()
                                    onMiniPlayerUpdate?.invoke()
                                    refreshService(context)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- TRUE SHUFFLE: Next & Previous now just cleanly walk the list ---
    fun playNext(context: Context) {
        if (currentPlaylist.isEmpty()) return

        if (!isRepeatEnabled) {
            // Because the deck is pre-shuffled, we ALWAYS just move +1 sequentially!
            currentIndex = (currentIndex + 1) % currentPlaylist.size
        }
        startPlaying(context, currentPlaylist, currentIndex)
    }

    fun playPrevious(context: Context) {
        if (currentPlaylist.isEmpty()) return

        if (!isRepeatEnabled) {
            // Because the deck is pre-shuffled, we ALWAYS just move -1 sequentially!
            currentIndex = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
        }
        startPlaying(context, currentPlaylist, currentIndex)
    }

    // --- THE MASTER SHUFFLE LOGIC ---
    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled

        if (currentPlaylist.isNotEmpty() && currentIndex >= 0 && currentIndex < currentPlaylist.size) {
            val currentActiveSong = currentPlaylist[currentIndex]

            if (isShuffleEnabled) {
                // TRUE SHUFFLE: Shuffle the upcoming songs, but keep the current song glued to the top!
                val remainingSongs = currentPlaylist.toMutableList()
                remainingSongs.removeAt(currentIndex)
                remainingSongs.shuffle() // Physically shuffle the deck

                val newPlaylist = mutableListOf(currentActiveSong)
                newPlaylist.addAll(remainingSongs)

                currentPlaylist = newPlaylist
                currentIndex = 0 // Our song is now technically at the very beginning of the new shuffled list
            } else {
                // SHUFFLE OFF: Restore the original, un-shuffled sequence
                currentPlaylist = originalPlaylist.toList()

                // Find where our current song lives in the original list so playback doesn't randomly jump!
                currentIndex = currentPlaylist.indexOfFirst { it.path == currentActiveSong.path }
                if (currentIndex == -1) currentIndex = 0
            }
        }

        onPlayerStateChanged?.invoke()
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        onPlayerStateChanged?.invoke()
    }

    fun play(context: Context) {
        if (mediaPlayer == null && currentSong != null && allSongs.isNotEmpty()) {
            val index = allSongs.indexOfFirst { it.title == currentSong?.title }
            startPlaying(context, allSongs, if (index >= 0) index else 0)
            return
        }

        if (requestAudioFocus()) {
            mediaPlayer?.start()
            isPlaying = true
            onPlayerStateChanged?.invoke()
            onMiniPlayerUpdate?.invoke()
            refreshService(context)
        }
    }

    fun pause(context: Context) {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlaying = false

            val currentPos = mediaPlayer?.currentPosition ?: 0
            savePlaybackState(context, currentPos)

            onMiniPlayerUpdate?.invoke()
            onPlayerStateChanged?.invoke()
        }
    }

    fun seekTo(context: Context, position: Int) {
        mediaPlayer?.seekTo(position)
        refreshService(context)
    }

    fun toggleFavorite(context: Context) {
        loadFavorites(context)
        currentSong?.let {
            if (favoriteSongs.contains(it.path)) favoriteSongs.remove(it.path)
            else favoriteSongs.add(it.path)

            saveFavorites(context)
            onPlayerStateChanged?.invoke()
            refreshService(context)
        }
    }

    private fun refreshService(context: Context) {
        val intent = Intent(context, MusicService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun savePlaybackState(context: Context, currentPositionMs: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
            val gson = Gson()

            val safeQueue = allSongs.map { it.copy(art = null) }
            val safeSong = currentSong?.copy(art = null)

            val queueJson = gson.toJson(safeQueue)
            val songJson = gson.toJson(safeSong)

            prefs.edit().apply {
                putString("SAVED_QUEUE", queueJson)
                putString("SAVED_SONG", songJson)
                putInt("SAVED_POSITION", currentPositionMs)
                apply()
            }
        }
    }

    fun restorePlaybackState(context: Context): Boolean {
        val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val queueJson = prefs.getString("SAVED_QUEUE", null)
        val songJson = prefs.getString("SAVED_SONG", null)

        if (queueJson != null && songJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<Song>>() {}.type

            allSongs = gson.fromJson(queueJson, type)
            currentSong = gson.fromJson(songJson, Song::class.java)
            savedPlaybackPosition = prefs.getInt("SAVED_POSITION", 0)

            currentSong?.let { song ->
                if (!song.isOnline) {
                    Thread {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(song.path)
                            val artBytes = retriever.embeddedPicture
                            val realArt = if (artBytes != null) BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size) else null
                            retriever.release()

                            currentSong = song.copy(art = realArt)

                            Handler(Looper.getMainLooper()).post {
                                onMiniPlayerUpdate?.invoke()
                            }
                        } catch (e: Exception) {}
                    }.start()
                }
            }

            return true
        }
        return false
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager?.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}