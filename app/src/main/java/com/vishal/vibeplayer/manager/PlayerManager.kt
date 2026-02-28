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

object PlayerManager {

    var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
    var currentSong: Song? = null
    var onPlayerStateChanged: (() -> Unit)? = null

    // --- NEW: The second megaphone for the Mini-Player! ---
    var onMiniPlayerUpdate: (() -> Unit)? = null

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

    // --- Data Persistence: Load from Phone Memory ---
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

    // --- Data Persistence: Save to Phone Memory ---
    private fun saveFavorites(context: Context) {
        val prefs = context.getSharedPreferences("VibePlayerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("FAVORITES", favoriteSongs.toSet()).apply()
    }

    fun startPlaying(context: Context, playlist: List<Song>, index: Int) {
        this.appContext = context.applicationContext

        // Ensure favorites are loaded before starting
        loadFavorites(context)

        if (transitionWakeLock == null) {
            val powerManager = appContext?.getSystemService(Context.POWER_SERVICE) as PowerManager
            transitionWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibePlayer::TransitionLock")
        }
        transitionWakeLock?.acquire(3000)

        currentPlaylist = playlist
        currentIndex = index
        val song = playlist[index]

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

            // --- THE FIX: Back to the stable synchronous prepare! ---
            mediaPlayer?.prepare()

            mediaPlayer?.setOnCompletionListener {
                appContext?.let { ctx -> playNext(ctx) }
            }

            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focusGranted = requestAudioFocus()

            if (focusGranted) {
                // Instantly play to avoid UI freezes!
                mediaPlayer?.start()
                isPlaying = true
                onPlayerStateChanged?.invoke()
                onMiniPlayerUpdate?.invoke() // <-- Trigger Mini-Player

                val intent = Intent(context, MusicService::class.java)
                ContextCompat.startForegroundService(context, intent)

                // Load the heavy album art on a background thread
                Thread {
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
                                onMiniPlayerUpdate?.invoke() // <-- Trigger Mini-Player art update
                                refreshService(context)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNext(context: Context) {
        if (currentPlaylist.isEmpty()) return

        if (isRepeatEnabled) {
            // Loop same song
        } else if (isShuffleEnabled) {
            currentIndex = (0 until currentPlaylist.size).random()
        } else {
            currentIndex = (currentIndex + 1) % currentPlaylist.size
        }

        startPlaying(context, currentPlaylist, currentIndex)
    }

    fun playPrevious(context: Context) {
        if (currentPlaylist.isEmpty()) return

        if (isRepeatEnabled) {
            // Loop same song
        } else if (isShuffleEnabled) {
            currentIndex = (0 until currentPlaylist.size).random()
        } else {
            currentIndex = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
        }

        startPlaying(context, currentPlaylist, currentIndex)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        onPlayerStateChanged?.invoke()
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        onPlayerStateChanged?.invoke()
    }

    fun play(context: Context) {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            isPlaying = true
            onPlayerStateChanged?.invoke()
            onMiniPlayerUpdate?.invoke() // <-- Trigger Mini-Player play icon
            refreshService(context)
        }
    }

    fun pause(context: Context) {
        mediaPlayer?.pause()
        isPlaying = false
        onPlayerStateChanged?.invoke()
        onMiniPlayerUpdate?.invoke() // <-- Trigger Mini-Player pause icon
        refreshService(context)
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