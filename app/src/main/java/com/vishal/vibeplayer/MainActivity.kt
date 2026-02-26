package com.vishal.vibeplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.vishal.vibeplayer.manager.PlayerManager

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val miniPlayer = findViewById<ConstraintLayout>(R.id.layoutMiniPlayer)
        val btnMiniPlayPause = findViewById<ImageView>(R.id.btnMiniPlayPause)
        val miniProgress = findViewById<ProgressBar>(R.id.miniPlayerProgress)

        // 1. Find the Mini Player text boxes
        val txtMiniTitle = findViewById<TextView>(R.id.txtMiniPlayerTitle)
        val txtMiniArtist = findViewById<TextView>(R.id.txtMiniPlayerArtist)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav.setupWithNavController(navController)

        miniPlayer.setOnClickListener {
            navController.navigate(R.id.playerFragment)
        }

        btnMiniPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause()
            else PlayerManager.play()
        }

        // 2. The Loop: Continuously sync text, icons, and progress!
        progressRunnable = Runnable {
            // A. Sync the Text & Image
            PlayerManager.currentSong?.let { song ->
                txtMiniTitle.text = song.title
                txtMiniArtist.text = song.artist

                // Inject the real Album Art into the Mini Player!
                val imgMiniPlayerArt = findViewById<ImageView>(R.id.imgMiniPlayerArt)
                if (song.art != null) {
                    imgMiniPlayerArt.setImageBitmap(song.art)
                } else {
                    // Show a default icon if the MP3 has no art
                    imgMiniPlayerArt.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

            // B. Sync the Play/Pause Icon
            if (PlayerManager.isPlaying) {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }

            // C. Sync the Progress Bar
            PlayerManager.mediaPlayer?.let { player ->
                if (PlayerManager.isPlaying) {
                    miniProgress.max = player.duration
                    miniProgress.progress = player.currentPosition
                }
            }
            handler.postDelayed(progressRunnable, 1000)
        }
        handler.postDelayed(progressRunnable, 1000)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.splashFragment, R.id.playerFragment, R.id.playlistDetailsFragment,
                R.id.appSettingsFragment, R.id.editProfileFragment, R.id.helpSupportFragment, R.id.aboutAppFragment -> {
                    bottomNav.visibility = View.GONE
                }
                else -> bottomNav.visibility = View.VISIBLE
            }

            when (destination.id) {
                R.id.splashFragment, R.id.playerFragment -> miniPlayer.visibility = View.GONE
                else -> miniPlayer.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
    }
}