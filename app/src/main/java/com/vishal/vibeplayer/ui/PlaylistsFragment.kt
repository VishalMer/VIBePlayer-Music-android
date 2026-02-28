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

        val rvFeatured = view.findViewById<RecyclerView>(R.id.rvFeaturedPlaylists)
        val featuredData = listOf(
            Playlist("Liked Songs", ""),
            Playlist("Party Hits", ""),
            Playlist("Chill Mix", ""),
            Playlist("Workout", "")
        )
        rvFeatured.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Pass the title to the Details Screen
        rvFeatured.adapter = FeaturedPlaylistAdapter(featuredData) { clickedPlaylist ->
            val bundle = Bundle()
            bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)
            findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
        }

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

        // Pass the title to the Details Screen
        rvYour.adapter = YourPlaylistAdapter(yourData) { clickedPlaylist ->
            val bundle = Bundle()
            bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)
            findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
        }

        return view
    }
}