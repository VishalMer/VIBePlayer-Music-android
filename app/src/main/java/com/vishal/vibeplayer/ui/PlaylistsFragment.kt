package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.FeaturedPlaylistAdapter
import com.vishal.vibeplayer.adapter.YourPlaylistAdapter
import com.vishal.vibeplayer.model.Playlist

class PlaylistsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)

        // 1. Setup Featured Playlists (Horizontal)
        val rvFeatured = view.findViewById<RecyclerView>(R.id.rvFeaturedPlaylists)
        val featuredData = listOf(
            Playlist("Liked Songs", ""),
            Playlist("Party Hits", ""),
            Playlist("Chill Mix", ""),
            Playlist("Workout", "")
        )
        rvFeatured.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvFeatured.adapter = FeaturedPlaylistAdapter(featuredData)

        // 2. Setup Your Playlists (Vertical)
        val rvYour = view.findViewById<RecyclerView>(R.id.rvYourPlaylists)
        val yourData = listOf(
            Playlist("My Favorites", "125 Songs • By You"),
            Playlist("Road Trip", "155 Songs • By You"),
            Playlist("Study Vibes", "14 Songs • By You"),
            Playlist("Coding Flow", "88 Songs • By You"),
            Playlist("Lo-Fi Beats", "42 Songs • By You"),
            Playlist("Top 50", "50 Songs • VibePlayer")
        )

        rvYour.layoutManager = LinearLayoutManager(requireContext())

        // 3. Pass the Navigation Action into the Adapter!
        rvYour.adapter = YourPlaylistAdapter(yourData) {
            findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment)
        }

        return view
    }
}