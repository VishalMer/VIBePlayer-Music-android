package com.vishal.vibeplayer.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.AppState

class AllTracksFragment : Fragment(R.layout.fragment_all_tracks) {

    // THE FIX: This "Memory Lock" remembers what is currently on the screen.
    // It prevents the FragmentManager from double-firing and crashing the app!
    private var currentlyLoadedMode: Boolean? = null

    override fun onResume() {
        super.onResume()
        // We ONLY check this in onResume now. No more double-firing!
        updateTrackScreen()
    }

    private fun updateTrackScreen() {
        // Safety check to ensure the fragment is fully attached to the screen
        if (!isAdded) return

        val prefs = requireContext().getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val isOnline = prefs.getBoolean("IS_ONLINE_MODE", false)
        AppState.isOnlineMode = isOnline

        // THE LOCK: Only trigger a massive screen swap if the mode ACTUALLY changed.
        if (currentlyLoadedMode != isOnline) {

            // Lock it in so it doesn't trigger again
            currentlyLoadedMode = isOnline

            val targetTag = if (isOnline) "ONLINE_TRACKS" else "LOCAL_TRACKS"
            val newFragment = if (isOnline) OnlineTracksFragment() else LocalTracksFragment()

            childFragmentManager.beginTransaction()
                .replace(R.id.tracks_host_container, newFragment, targetTag)
                .commitAllowingStateLoss()
        }
    }
}