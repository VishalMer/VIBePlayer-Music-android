package com.vishal.vibeplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.PlayerManager
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment() {

    private lateinit var runnable: Runnable
    private var handler = Handler(Looper.getMainLooper())

    private lateinit var imgPlayPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView

    private lateinit var txtPlayerTitle: TextView
    private lateinit var txtPlayerArtist: TextView
    private lateinit var imgPlayerArt: ImageView

    private lateinit var btnFavorite: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        // 1. Find all views
        val btnPlayPause = view.findViewById<View>(R.id.btnPlayPause)
        imgPlayPause = view.findViewById(R.id.imgPlayPause)
        val btnDownPlayer = view.findViewById<View>(R.id.btnDownPlayer)
        val btnNext = view.findViewById<ImageView>(R.id.btnNext)
        val btnPrevious = view.findViewById<ImageView>(R.id.btnPrev)

        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)

        seekBar = view.findViewById(R.id.seekBarPlayer)
        txtCurrentTime = view.findViewById(R.id.txtCurrentTime)
        txtTotalTime = view.findViewById(R.id.txtTotalTime)

        txtPlayerTitle = view.findViewById(R.id.txtPlayerTitle)
        txtPlayerArtist = view.findViewById(R.id.txtPlayerArtist)
        imgPlayerArt = view.findViewById(R.id.imgPlayerArt)

        // --- ADD THE MARQUEE MAGIC LINE HERE ---
        txtPlayerTitle.isSelected = true

        // 2. Setup Click Listeners
        btnNext?.setOnClickListener { PlayerManager.playNext(requireContext()) }
        btnPrevious?.setOnClickListener { PlayerManager.playPrevious(requireContext()) }
        btnShuffle.setOnClickListener { PlayerManager.toggleShuffle() }
        btnRepeat.setOnClickListener { PlayerManager.toggleRepeat() }
        btnFavorite.setOnClickListener { PlayerManager.toggleFavorite(requireContext()) }

        btnPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause(requireContext())
            else PlayerManager.play(requireContext())
        }

        btnDownPlayer.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    PlayerManager.mediaPlayer?.seekTo(progress)
                    txtCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. Initial UI Load
        updateUI(view)

        // 4. Listen to the Global Brain!
        // This completely replaces the nested, duplicate code from before.
        PlayerManager.onPlayerStateChanged = {
            activity?.runOnUiThread {
                updateUI(view)
            }
        }

        return view
    }

    // THE MASTER UI UPDATER
    // We pass 'view' in so we can update the background dynamically!
    private fun updateUI(view: View) {
        PlayerManager.currentSong?.let { song ->
            txtPlayerTitle.text = song.title
            txtPlayerArtist.text = song.artist

            if (song.art != null) {
                imgPlayerArt.setImageBitmap(song.art)
            } else {
                imgPlayerArt.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            // Trigger the dynamic background update!
            updateDynamicBackground(view, song.art)
        }

        // 1. Shuffle State
        if (PlayerManager.isShuffleEnabled) {
            btnShuffle.setColorFilter(Color.parseColor("#1DB954")) // Spotify Green
        } else {
            btnShuffle.setColorFilter(Color.WHITE)
        }

        // 2. Repeat State
        if (PlayerManager.isRepeatEnabled) {
            btnRepeat.setColorFilter(Color.parseColor("#1DB954"))
        } else {
            btnRepeat.setColorFilter(Color.WHITE)
        }

        // 3. Favorite State
        val isFav = PlayerManager.favoriteSongs.contains(PlayerManager.currentSong?.path)
        if (isFav) {
            btnFavorite.setImageResource(android.R.drawable.star_on)
            btnFavorite.setColorFilter(Color.parseColor("#1DB954"))
        } else {
            btnFavorite.setImageResource(android.R.drawable.star_off)
            btnFavorite.setColorFilter(Color.WHITE)
        }

        // 4. Play/Pause & Seekbar State
        if (PlayerManager.isPlaying) {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            startSeekBarLoop()
        } else {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_play)
            if (::runnable.isInitialized) handler.removeCallbacks(runnable)
        }

        // 5. Update Duration
        PlayerManager.mediaPlayer?.let { player ->
            seekBar.max = player.duration
            txtTotalTime.text = formatTime(player.duration)
        }
    }

    private fun startSeekBarLoop() {
        if (::runnable.isInitialized) handler.removeCallbacks(runnable)
        runnable = Runnable {
            PlayerManager.mediaPlayer?.let { player ->
                if (PlayerManager.isPlaying) {
                    seekBar.progress = player.currentPosition
                    txtCurrentTime.text = formatTime(player.currentPosition)
                    handler.postDelayed(runnable, 1000)
                }
            }
        }
        handler.postDelayed(runnable, 1000)
    }

    private fun formatTime(ms: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateDynamicBackground(view: View, bitmap: Bitmap?) {
        val rootLayout = view.findViewById<View>(R.id.playerRootLayout)
        val defaultDarkColor = Color.parseColor("#121212")

        if (bitmap == null) {
            rootLayout.setBackgroundColor(defaultDarkColor)
            return
        }

        Palette.from(bitmap).generate { palette ->
            val extractedColor = palette?.darkVibrantSwatch?.rgb
                ?: palette?.dominantSwatch?.rgb
                ?: defaultDarkColor

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(extractedColor, defaultDarkColor)
            )
            gradientDrawable.cornerRadius = 0f

            rootLayout.background = gradientDrawable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        PlayerManager.onPlayerStateChanged = null
    }
}