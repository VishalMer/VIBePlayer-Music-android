package com.vishal.vibeplayer

import android.content.Intent
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
import com.bumptech.glide.Glide // Needed for online covers!
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.vishal.vibeplayer.manager.FirebaseManager
import com.vishal.vibeplayer.manager.PlayerManager

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable
    private var isPlayerFragmentVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. AUTH CHECK: If the user is NOT logged in, send them to Register and stop loading Main!
        if (!FirebaseManager.isUserLoggedIn()) {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish() // Closes MainActivity so they can't press 'Back' to get in
            return
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setupWithNavController(navController)

        // 2. SETUP UI FIRST: Connect the Mini-Player logic
        setupMiniPlayer(navController, bottomNav)

        // 3. RESTORE SESSION: Check memory and trigger the UI if a song is found!
        val hasSavedSession = PlayerManager.restorePlaybackState(this)
        if (hasSavedSession) {
            // This manually triggers your onMiniPlayerUpdate lambda below!
            PlayerManager.onMiniPlayerUpdate?.invoke()
        }
    }

    private fun setupMiniPlayer(navController: NavController, bottomNav: BottomNavigationView) {
        val layoutMiniPlayer = findViewById<View>(R.id.layoutMiniPlayer)
        val imgMiniPlayerArt = findViewById<ImageView>(R.id.imgMiniPlayerArt)
        val txtMiniPlayerTitle = findViewById<TextView>(R.id.txtMiniPlayerTitle)
        val txtMiniPlayerArtist = findViewById<TextView>(R.id.txtMiniPlayerArtist)
        val btnMiniPlayPause = findViewById<ImageView>(R.id.btnMiniPlayPause)
        val miniPlayerProgress = findViewById<ProgressBar>(R.id.miniPlayerProgress)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.playerFragment) {
                isPlayerFragmentVisible = true
                bottomNav.visibility = View.GONE
                layoutMiniPlayer.visibility = View.GONE
            } else {
                isPlayerFragmentVisible = false
                bottomNav.visibility = View.VISIBLE
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
                    if (!isPlayerFragmentVisible) {
                        layoutMiniPlayer.visibility = View.VISIBLE
                    }

                    val song = PlayerManager.currentSong!!
                    txtMiniPlayerTitle.text = song.title
                    txtMiniPlayerArtist.text = song.artist

                    // --- NEW SMART IMAGE LOADER FOR MINI-PLAYER ---
                    if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
                        // Load Jamendo URL
                        Glide.with(this@MainActivity)
                            .load(song.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(imgMiniPlayerArt)
                    } else if (song.art != null) {
                        // Load Local Offline Bitmap
                        imgMiniPlayerArt.setImageBitmap(song.art)
                    } else {
                        imgMiniPlayerArt.setImageResource(R.drawable.bg_default_cover)
                    }

                    if (PlayerManager.isPlaying) {
                        btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
                        handler.post(progressRunnable)
                    } else {
                        btnMiniPlayPause.setImageResource(R.drawable.ic_play)
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