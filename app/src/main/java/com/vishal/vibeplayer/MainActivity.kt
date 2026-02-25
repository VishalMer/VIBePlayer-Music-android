package com.vishal.vibeplayer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Find our UI components
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val miniPlayer = findViewById<ConstraintLayout>(R.id.layoutMiniPlayer)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 2. Connect the Bottom Bar to the Navigation Map
        bottomNav.setupWithNavController(navController)

        // 3. Make the Mini Player clickable!
        miniPlayer.setOnClickListener {
            navController.navigate(R.id.playerFragment)
        }

        // 4. Smart Visibility: Hide bars on specific screens
        navController.addOnDestinationChangedListener { _, destination, _ ->

            // Manage Bottom Navigation Visibility
            when (destination.id) {
                R.id.splashFragment,
                R.id.playerFragment,
                R.id.playlistDetailsFragment,
                R.id.appSettingsFragment,
                R.id.editProfileFragment,
                R.id.helpSupportFragment,
                R.id.aboutAppFragment -> {
                    bottomNav.visibility = View.GONE
                }
                else -> {
                    bottomNav.visibility = View.VISIBLE
                }
            }

            // Manage Mini Player Visibility
            // We only want to hide the Mini Player on the Splash screen and the Full Player screen
            when (destination.id) {
                R.id.splashFragment,
                R.id.playerFragment -> {
                    miniPlayer.visibility = View.GONE
                }
                else -> {
                    miniPlayer.visibility = View.VISIBLE
                }
            }
        }
    }
}