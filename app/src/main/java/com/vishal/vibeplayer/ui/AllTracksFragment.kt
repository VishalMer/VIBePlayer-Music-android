package com.vishal.vibeplayer.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.AppState

class AllTracksFragment : Fragment() {

    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_all_tracks, container, false)
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)
        val isOnline = prefs.getBoolean("IS_ONLINE_MODE", false)
        AppState.isOnlineMode = isOnline

        // 1. Assign a specific name (Tag) to the fragment we want to show
        val targetTag = if (isOnline) "ONLINE_TRACKS" else "LOCAL_TRACKS"

        // 2. Check what fragment is currently sitting in the container
        val currentFrag = childFragmentManager.findFragmentById(R.id.tracks_host_container)

        // 3. Only do a heavy transaction if the wrong fragment (or no fragment) is showing
        if (currentFrag?.tag != targetTag) {

            // Search memory to see if we already built this fragment previously!
            var fragmentToShow = childFragmentManager.findFragmentByTag(targetTag)

            if (fragmentToShow == null) {
                // ONLY create a brand new instance if it has never been opened before
                fragmentToShow = if (isOnline) OnlineTracksFragment() else LocalTracksFragment()
            }

            // Swap them out and attach the Tag so we can find it later
            childFragmentManager.beginTransaction()
                .replace(R.id.tracks_host_container, fragmentToShow, targetTag)
                .commit()
        }
    }
}