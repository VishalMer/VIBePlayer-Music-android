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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vishal.vibeplayer.manager.FirebaseManager
import com.vishal.vibeplayer.manager.PlayerManager
import android.graphics.Color
import android.view.animation.OvershootInterpolator
import androidx.navigation.NavOptions

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable
    private var isPlayerFragmentVisible = false

    private lateinit var navController: NavController
    private lateinit var navActiveIndicator: View
    private lateinit var icons: List<ImageView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. AUTH CHECK
        if (!FirebaseManager.isUserLoggedIn()) {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
            return
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navActiveIndicator = findViewById(R.id.navActiveIndicator)
        val navHome = findViewById<ImageView>(R.id.navHome)
        val navTracks = findViewById<ImageView>(R.id.navTracks)
        val navSearch = findViewById<ImageView>(R.id.navSearch)
        val navPlaylists = findViewById<ImageView>(R.id.navPlaylists)
        val navProfile = findViewById<ImageView>(R.id.navProfile)

        icons = listOf(navHome, navTracks, navSearch, navPlaylists, navProfile)

        navHome.post { slidePillTo(navHome, animate = false) }

        // THE FIX: Treat the bottom bar as a standard View now!
        val bottomNav = findViewById<View>(R.id.customBottomBar)

        // 2. SETUP UI FIRST
        setupMiniPlayer(navController, bottomNav)

        // 3. RESTORE SESSION
        val hasSavedSession = PlayerManager.restorePlaybackState(this)
        if (hasSavedSession) {
            PlayerManager.onMiniPlayerUpdate?.invoke()
        }

        // --- CLICK LISTENERS ---
        navHome.setOnClickListener {
            slidePillTo(navHome)
            navigateToTab(R.id.homeFragment)
        }

        navTracks.setOnClickListener {
            slidePillTo(navTracks)
            navigateToTab(R.id.allTracksFragment)
        }

        navSearch.setOnClickListener {
            slidePillTo(navSearch)
            navigateToTab(R.id.searchFragment)
        }

        navPlaylists.setOnClickListener {
            slidePillTo(navPlaylists)
            navigateToTab(R.id.playlistsFragment)
        }

        navProfile.setOnClickListener {
            slidePillTo(navProfile)
            navigateToTab(R.id.profileFragment)
        }

        // Modern Back Button Handling
        // Modern Back Button Handling (Crash-Free & Custom Pill Support)
        // Modern Back Button Handling (Crash-Free & Custom Pill Support)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentId = navController.currentDestination?.id
                val mainTabIds = listOf(
                    R.id.homeFragment,
                    R.id.allTracksFragment,
                    R.id.searchFragment,
                    R.id.playlistsFragment,
                    R.id.profileFragment
                )

                if (currentId !in mainTabIds) {
                    // Deep inside a screen: Go back normally
                    navController.popBackStack()
                } else if (currentId != R.id.homeFragment) {
                    // On a main tab: Slide pill and INSTANTLY pop back to home
                    slidePillTo(navHome)

                    // THE SPEED FIX: Pop the stack instead of navigating forward!
                    // This eliminates the fragment loading lag.
                    val safelyPopped = navController.popBackStack(R.id.homeFragment, false)

                    // Failsafe just in case Home was accidentally destroyed
                    if (!safelyPopped) {
                        navigateToTab(R.id.homeFragment)
                    }
                } else {
                    // On Home tab: Exit app
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun slidePillTo(selectedIcon: ImageView, animate: Boolean = true) {
        val targetX = selectedIcon.x + (selectedIcon.width / 2f) - (navActiveIndicator.width / 2f)

        if (animate) {
            navActiveIndicator.animate()
                .translationX(targetX)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        } else {
            navActiveIndicator.translationX = targetX
        }

        icons.forEach { icon ->
            if (icon == selectedIcon) {
                icon.setColorFilter(Color.WHITE)
            } else {
                icon.setColorFilter(Color.parseColor("#A0A0A0"))
            }
        }
    }

    // THE FIX: Changed 'BottomNavigationView' to 'View' here too!
    private fun setupMiniPlayer(navController: NavController, bottomNav: View) {
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

                    if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
                        Glide.with(this@MainActivity)
                            .load(song.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(imgMiniPlayerArt)
                    } else if (song.art != null) {
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

    private fun navigateToTab(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return

        // This tells Android to act exactly like a standard Bottom Navigation Bar
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true) // Don't create duplicates! Use the one in memory.
            .setRestoreState(true)    // Remember exactly where we scrolled!
            .setPopUpTo(
                navController.graph.startDestinationId,
                inclusive = false,
                saveState = true
            )
            .build()

        navController.navigate(destinationId, null, navOptions)
    }
}