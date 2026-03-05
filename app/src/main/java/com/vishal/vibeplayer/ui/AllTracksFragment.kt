package com.vishal.vibeplayer.ui

import android.content.Context // Make sure this is imported!
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.AppState

class AllTracksFragment : Fragment(R.layout.fragment_all_tracks) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Read the REAL saved state from device memory
        val prefs = requireContext().getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val isOnline = prefs.getBoolean("IS_ONLINE_MODE", false)

        // 2. Sync it with our global state so it's accurate
        AppState.isOnlineMode = isOnline

        // 3. Show the correct screen!
        val fragmentToShow = if (isOnline) {
            OnlineTracksFragment()
        } else {
            LocalTracksFragment()
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.tracks_host_container, fragmentToShow)
            .commit()
    }
}