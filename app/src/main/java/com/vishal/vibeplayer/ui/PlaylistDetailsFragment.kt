package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import com.vishal.vibeplayer.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailsFragment : Fragment() {

    // IMPORTANT: Paste your Jamendo Client ID here!
    private val JAMENDO_CLIENT_ID = "1287b878"

    // We keep this at the class level so the "Play All" button always knows what songs to play
    private var displaySongs: List<Song> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlist_details, container, false)

        // Find your exact XML IDs
        val txtTitle = view.findViewById<TextView>(R.id.txtDetailTitle)
        val txtSubtitle = view.findViewById<TextView>(R.id.txtDetailSubtitle)
        val rvPlaylistSongs = view.findViewById<RecyclerView>(R.id.rvPlaylistSongs)
        val btnBack = view.findViewById<View>(R.id.btnBackPlaylist)
        val btnPlayAll = view.findViewById<View>(R.id.btnPlayAll)

        // **IMPORTANT:** Make sure your large album square in XML has this exact ID!
        val ivCover = view.findViewById<ImageView>(R.id.ivPlaylistCover)

        // Check which screen sent us here
        val playlistName = arguments?.getString("PLAYLIST_NAME")
        val albumId = arguments?.getString("ALBUM_ID")
        val albumName = arguments?.getString("ALBUM_NAME")
        val albumArt = arguments?.getString("ALBUM_ART")
        val albumArtist = arguments?.getString("ALBUM_ARTIST")

        rvPlaylistSongs?.layoutManager = LinearLayoutManager(requireContext())

        if (albumId != null) {
            // ==========================================
            // SCENARIO 1: ONLINE JAMENDO ALBUM
            // ==========================================
            txtTitle?.text = albumName ?: "Unknown Album"
            txtSubtitle?.text = albumArtist ?: "Unknown Artist"

            // Load the big album cover using Glide!
            if (ivCover != null && albumArt != null) {
                Glide.with(this)
                    .load(albumArt)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivCover)
            }

            // Fetch the internet tracks in the background
            fetchAlbumTracks(albumId, rvPlaylistSongs, albumArt ?: "", txtSubtitle, albumArtist)

        } else {
            // ==========================================
            // SCENARIO 2: OFFLINE LOCAL PLAYLIST
            // ==========================================
            txtTitle?.text = playlistName ?: "Unknown Playlist"

            if (playlistName == "Liked Songs" || playlistName == "My Favorites") {
                PlayerManager.loadFavorites(requireContext())
                val allSongsOnDevice = PlayerManager.allSongs

                displaySongs = allSongsOnDevice.filter { song ->
                    PlayerManager.favoriteSongs.contains(song.path)
                }
                txtSubtitle?.text = "${displaySongs.size} Songs • By You"
            } else {
                txtSubtitle?.text = "0 Songs"
            }

            // Attach the local songs to the adapter
            rvPlaylistSongs?.adapter = SongAdapter(displaySongs) { clickedSong ->
                val index = displaySongs.indexOf(clickedSong)
                PlayerManager.startPlaying(requireContext(), displaySongs, index)
            }
        }

        // --- BUTTON CLICKS ---
        btnPlayAll?.setOnClickListener {
            if (displaySongs.isNotEmpty()) {
                // Plays the entire list starting from the 1st song!
                PlayerManager.startPlaying(requireContext(), displaySongs, 0)
            }
        }

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }

    // Notice I added 'albumArtist' to the parameters here!
    private fun fetchAlbumTracks(albumId: String, rvTracks: RecyclerView?, albumArt: String, txtSubtitle: TextView?, albumArtist: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getTracksByAlbum(
                    clientId = JAMENDO_CLIENT_ID,
                    albumId = albumId
                )

                withContext(Dispatchers.Main) {
                    val tracks = response.results.firstOrNull()?.tracks ?: emptyList()

                    // Safely map the data, providing fallbacks if Jamendo misses a field
                    displaySongs = tracks.map { track ->
                        Song(
                            title = track.title ?: "Unknown Track",
                            artist = track.artist ?: albumArtist ?: "Unknown Artist",
                            duration = formatDuration(track.duration ?: 0),
                            path = track.audioUrl ?: "",
                            isOnline = true,
                            imageUrl = track.imageUrl ?: albumArt
                        )
                    }

                    txtSubtitle?.text = "${displaySongs.size} Tracks"

                    rvTracks?.adapter = SongAdapter(displaySongs) { clickedSong ->
                        val clickedIndex = displaySongs.indexOf(clickedSong)
                        PlayerManager.startPlaying(requireContext(), displaySongs, clickedIndex)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API_ERROR", "Failed to load album tracks", e)
                    Toast.makeText(requireContext(), "Failed to load tracks. Check internet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Now cleanly takes an Int without crashing
    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}