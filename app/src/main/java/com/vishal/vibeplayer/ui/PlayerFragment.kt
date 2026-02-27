package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
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

    // 1. Declare the Image Box up here so the whole file can see it!
    private lateinit var imgPlayerArt: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        val btnPlayPause = view.findViewById<CardView>(R.id.btnPlayPause)
        imgPlayPause = view.findViewById(R.id.imgPlayPause)
        val btnDownPlayer = view.findViewById<View>(R.id.btnDownPlayer)

        seekBar = view.findViewById(R.id.seekBarPlayer)
        txtCurrentTime = view.findViewById(R.id.txtCurrentTime)
        txtTotalTime = view.findViewById(R.id.txtTotalTime)

        txtPlayerTitle = view.findViewById(R.id.txtPlayerTitle)
        txtPlayerArtist = view.findViewById(R.id.txtPlayerArtist)

        // 2. Safely find the image box using the local 'view' variable
        imgPlayerArt = view.findViewById(R.id.imgPlayerArt)

        updateUI()

        PlayerManager.onPlayerStateChanged = {
            updateUI()
        }

        btnPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause()
            else PlayerManager.play()
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

        btnDownPlayer.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }

    private fun updateUI() {
        PlayerManager.currentSong?.let { song ->
            txtPlayerTitle.text = song.title
            txtPlayerArtist.text = song.artist

            // 3. Inject the image using our safe global variable!
            if (song.art != null) {
                imgPlayerArt.setImageBitmap(song.art)
            } else {
                imgPlayerArt.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        if (PlayerManager.isPlaying) {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            startSeekBarLoop()
        } else {
            imgPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        if (::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
        PlayerManager.onPlayerStateChanged = null
    }
}