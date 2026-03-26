package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.vishal.vibeplayer.R

class ProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Find the buttons using their XML IDs
        val btnSettings = view.findViewById<View>(R.id.btnSettings)
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)

        val cardMyLibrary = view.findViewById<View>(R.id.cardMyLibrary)
        val cardDownloads = view.findViewById<View>(R.id.cardDownloads)
        val cardHistory = view.findViewById<View>(R.id.cardHistory)
        val cardLikedSongs = view.findViewById<View>(R.id.cardLikedSongs)

        // 2. Set Click Listeners with Special Negative IDs
        cardMyLibrary?.setOnClickListener { openSpecialPlaylist("My Library", -2) }
        cardHistory?.setOnClickListener { openSpecialPlaylist("Listening History", -3) }
        cardDownloads?.setOnClickListener { openSpecialPlaylist("Downloads", -4) }

        cardLikedSongs?.setOnClickListener {
            openSpecialPlaylist("Liked Songs", -1)
        }

        // 3. Settings and Edit Profile Navigation
        btnSettings?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_appSettingsFragment)
        }

        btnEditProfile?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    private fun openSpecialPlaylist(title: String, specialId: Int) {
        val bundle = Bundle().apply {
            putString("PLAYLIST_NAME", title)
            putInt("CUSTOM_PLAYLIST_ID", specialId)
        }
        findNavController().navigate(R.id.playlistDetailsFragment, bundle)
    }
}