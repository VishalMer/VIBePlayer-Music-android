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

    var originalPlaylist: List<Song> = emptyList()
    var currentPlaylist: List<Song> = emptyList()
    var allSongs: List<Song> = emptyList()
    var currentIndex: Int = -1

    var isShuffleEnabled = false
    var isRepeatEnabled = false

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var resumeOnFocusGain = false

    private var transitionWakeLock: PowerManager.WakeLock? = null
    private var isFavoritesLoaded = false
    var savedPlaybackPosition: Int = 0
    var playHistory = mutableListOf<Song>()
    var playCounts = mutableMapOf<String, Int>()
    val favoriteSongs = mutableSetOf<String>()


    // --- This variable tracks the exact millisecond of the last skip ---
    private var lastSkipTime = 0L

    fun addToHistory(song: Song) {
        // --- EXISTING HISTORY LOGIC ---
        playHistory.removeAll { it.path == song.path }
        playHistory.add(0, song)
        if (playHistory.size > 50) {
            playHistory.removeAt(playHistory.lastIndex)
        }
        appContext?.let { saveHistory(it) }

        // --- NEW ANALYTICS LOGIC ---
        // 1. Get the current path (fallback to empty string if null)
        val path = song.path ?: ""
        if (path.isNotEmpty()) {
            // 2. Check the current count (default to 0 if it's never been played)
            val currentCount = playCounts[path] ?: 0
            // 3. Add 1 and save it!
            playCounts[path] = currentCount + 1
            appContext?.let { savePlayCounts(it) }
        }
    }

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

        val isSamePlaylist = (playlist == currentPlaylist)

        if (!isSamePlaylist) {
            originalPlaylist = playlist.toList()
            currentPlaylist = playlist.toMutableList()
            currentIndex = index

            if (isShuffleEnabled && currentPlaylist.size > 1) {
                val currentActiveSong = currentPlaylist[currentIndex]
                val remainingSongs = currentPlaylist.toMutableList()
                remainingSongs.removeAt(currentIndex)
                remainingSongs.shuffle()

                val newPlaylist = mutableListOf(currentActiveSong)
                newPlaylist.addAll(remainingSongs)

                currentPlaylist = newPlaylist
                currentIndex = 0
            }
        } else {
            currentIndex = index
        }

        val song = currentPlaylist[currentIndex]
        currentSong = song.copy(art = null)

        currentSong?.let { addToHistory(it) }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            }
        } else {
            mediaPlayer?.reset()
        }

        try {
            mediaPlayer?.setDataSource(song.path)

            mediaPlayer?.setOnPreparedListener { player ->
                if (savedPlaybackPosition > 0) {
                    player.seekTo(savedPlaybackPosition)
                    savedPlaybackPosition = 0
                }

                if (requestAudioFocus()) {
                    player.start()
                    isPlaying = true
                    onPlayerStateChanged?.invoke()
                    onMiniPlayerUpdate?.invoke()
                    refreshService(context)
                }
            }


            // instantly triggering "Next"

            mediaPlayer?.setOnErrorListener { _, _, _ ->
                isPlaying = false
                onPlayerStateChanged?.invoke()
                // Returning 'true' tells Android we handled the error, so do NOT auto-call onCompletion!
                true
            }

            mediaPlayer?.setOnCompletionListener {
                appContext?.let { ctx -> playNext(ctx) }
            }

            mediaPlayer?.prepareAsync()

            onPlayerStateChanged?.invoke()
            onMiniPlayerUpdate?.invoke()

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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNext(context: Context) {
        if (currentPlaylist.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < 400) return // If it's been less than 400ms since the last skip, IGNORE IT!
        lastSkipTime = currentTime

        if (!isRepeatEnabled) {
            currentIndex = (currentIndex + 1) % currentPlaylist.size
        }
        startPlaying(context, currentPlaylist, currentIndex)
    }

    fun playPrevious(context: Context) {
        if (currentPlaylist.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < 400) return
        lastSkipTime = currentTime

        if (!isRepeatEnabled) {
            currentIndex = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
        }
        startPlaying(context, currentPlaylist, currentIndex)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled

        if (currentPlaylist.isNotEmpty() && currentIndex >= 0 && currentIndex < currentPlaylist.size) {
            val currentActiveSong = currentPlaylist[currentIndex]

            if (isShuffleEnabled) {
                val remainingSongs = currentPlaylist.toMutableList()
                remainingSongs.removeAt(currentIndex)
                remainingSongs.shuffle()

                val newPlaylist = mutableListOf(currentActiveSong)
                newPlaylist.addAll(remainingSongs)

                currentPlaylist = newPlaylist
                currentIndex = 0
            } else {
                currentPlaylist = originalPlaylist.toList()
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

    // --- History Persistence ---
    private fun saveHistory(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
            val gson = Gson()

            // Strip the heavy album art images before saving to prevent crashes!
            val safeHistory = playHistory.map { it.copy(art = null) }
            val historyJson = gson.toJson(safeHistory)

            prefs.edit().putString("SAVED_HISTORY", historyJson).apply()
        }
    }

    private fun savePlayCounts(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
            val json = Gson().toJson(playCounts)
            prefs.edit().putString("PLAY_COUNTS", json).apply()
        }
    }

    private fun loadPlayCounts(context: Context) {
        val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("PLAY_COUNTS", null)
        if (json != null) {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            try {
                playCounts = Gson().fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadHistory(context: Context) {
        val prefs = context.getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("SAVED_HISTORY", null)

        if (historyJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<Song>>() {}.type
            try {
                val savedList: List<Song> = gson.fromJson(historyJson, type)
                playHistory = savedList.toMutableList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

        this.appContext = context.applicationContext
        loadHistory(context)
        loadPlayCounts(context)

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
        if (audioManager == null) {
            appContext?.let {
                audioManager = it.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            } ?: return false
        }

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