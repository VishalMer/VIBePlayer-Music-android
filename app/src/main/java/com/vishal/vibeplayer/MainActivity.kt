package com.vishal.vibeplayer // Make sure this matches your package name!

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.vishal.vibeplayer.manager.PlayerManager

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable

    // NEW: We need to track if the full player is open!
    private var isPlayerFragmentVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Pass bottomNav to our setup function so we can hide it too
        setupMiniPlayer(navController, bottomNav)
    }

    private fun setupMiniPlayer(navController: NavController, bottomNav: BottomNavigationView) {
        val layoutMiniPlayer = findViewById<View>(R.id.layoutMiniPlayer)
        val imgMiniPlayerArt = findViewById<ImageView>(R.id.imgMiniPlayerArt)
        val txtMiniPlayerTitle = findViewById<TextView>(R.id.txtMiniPlayerTitle)
        val txtMiniPlayerArtist = findViewById<TextView>(R.id.txtMiniPlayerArtist)
        val btnMiniPlayPause = findViewById<ImageView>(R.id.btnMiniPlayPause)
        val miniPlayerProgress = findViewById<ProgressBar>(R.id.miniPlayerProgress)

        // --- NEW: The Magic Listener that hides the bottom UI ---
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.playerFragment) { // Check if we opened the full player
                isPlayerFragmentVisible = true
                bottomNav.visibility = View.GONE
                layoutMiniPlayer.visibility = View.GONE
            } else { // We are on any other screen (Home, Playlists, etc.)
                isPlayerFragmentVisible = false
                bottomNav.visibility = View.VISIBLE

                // Only bring the Mini-Player back if a song is actually playing/loaded
                if (PlayerManager.currentSong != null) {
                    layoutMiniPlayer.visibility = View.VISIBLE
                }
            }
        }

        progressRunnable = object : Runnable {
            override fun run() {
                PlayerManager.mediaPlayer?.let { player ->
                    if (PlayerManager.isPlaying) {
                        miniPlayerProgress.max = player.duration
                        miniPlayerProgress.progress = player.currentPosition
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }

        PlayerManager.onMiniPlayerUpdate = {
            runOnUiThread {
                if (PlayerManager.currentSong != null) {

                    // NEW: Only unhide the Mini-Player if we ARE NOT on the full player screen!
                    if (!isPlayerFragmentVisible) {
                        layoutMiniPlayer.visibility = View.VISIBLE
                    }

                    val song = PlayerManager.currentSong!!
                    txtMiniPlayerTitle.text = song.title
                    txtMiniPlayerArtist.text = song.artist

                    if (song.art != null) {
                        imgMiniPlayerArt.setImageBitmap(song.art)
                    } else {
                        imgMiniPlayerArt.setImageResource(android.R.drawable.ic_menu_gallery)
                    }

                    if (PlayerManager.isPlaying) {
                        btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                        handler.post(progressRunnable)
                    } else {
                        btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        handler.removeCallbacks(progressRunnable)
                    }
                } else {
                    layoutMiniPlayer.visibility = View.GONE
                    handler.removeCallbacks(progressRunnable)
                }
            }
        }

        btnMiniPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying) PlayerManager.pause(this)
            else PlayerManager.play(this)
        }

        layoutMiniPlayer.setOnClickListener {
            navController.navigate(R.id.playerFragment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
    }
}