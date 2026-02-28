package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song

class PlaylistDetailsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlist_details, container, false)

        val playlistName = arguments?.getString("PLAYLIST_NAME") ?: "Unknown Playlist"

        // Matched to your EXACT XML IDs
        val txtTitle = view.findViewById<TextView>(R.id.txtDetailTitle)
        val txtSubtitle = view.findViewById<TextView>(R.id.txtDetailSubtitle)
        val rvPlaylistSongs = view.findViewById<RecyclerView>(R.id.rvPlaylistSongs)
        val btnBack = view.findViewById<View>(R.id.btnBackPlaylist)
        val btnPlayAll = view.findViewById<View>(R.id.btnPlayAll)

        txtTitle?.text = playlistName

        var displaySongs = emptyList<Song>()

        // 1. Filter the songs!
        if (playlistName == "Liked Songs" || playlistName == "My Favorites") {
            PlayerManager.loadFavorites(requireContext())

            // We grab the master list you saved from AllTracksFragment
            val allSongsOnDevice = PlayerManager.allSongs

            displaySongs = allSongsOnDevice.filter { song ->
                PlayerManager.favoriteSongs.contains(song.path)
            }

            txtSubtitle?.text = "${displaySongs.size} Songs • By You"
        } else {
            txtSubtitle?.text = "0 Songs"
        }

        // 2. Display the songs using your real SongAdapter!
        rvPlaylistSongs?.layoutManager = LinearLayoutManager(requireContext())
        rvPlaylistSongs?.adapter = SongAdapter(displaySongs) { clickedSong ->
            val index = displaySongs.indexOf(clickedSong)
            PlayerManager.startPlaying(requireContext(), displaySongs, index)
        }

        // 3. Handle Button Clicks
        btnPlayAll?.setOnClickListener {
            if (displaySongs.isNotEmpty()) {
                PlayerManager.startPlaying(requireContext(), displaySongs, 0)
            }
        }

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view // Safely returning the view at the very end!
    }
}