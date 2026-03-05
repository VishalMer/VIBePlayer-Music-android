package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.GenreAdapter
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineTracksFragment : Fragment(R.layout.fragment_online_tracks) {

    // TODO: Replace with your actual Jamendo API Client ID!
    private val JAMENDO_CLIENT_ID = "1287b878"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // FIX 1: Added savedInstanceState here
        super.onViewCreated(view, savedInstanceState)

        val rvTrending = view.findViewById<RecyclerView>(R.id.rvTrendingTracks)
        val rvMoods = view.findViewById<RecyclerView>(R.id.rvMoods)

        setupMoodGrid(rvMoods)
        fetchTrendingTracks(rvTrending)
    }

    private fun setupMoodGrid(rvMoods: RecyclerView) {
        val moods = listOf("Chill", "Workout", "Romantic", "Party", "Focus", "Pop")

        // FIX 2: Removed the click listener to perfectly match your GenreAdapter
        rvMoods.adapter = GenreAdapter(moods)
    }

    private fun fetchTrendingTracks(rvTrending: RecyclerView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch ALBUMS instead of single tracks!
                val response = RetrofitClient.apiService.getTrendingAlbums(clientId = JAMENDO_CLIENT_ID)

                withContext(Dispatchers.Main) {
                    // 2. Map the Album data to your Song model so the Square adapter can render it
                    val albums = response.results.map { album ->
                        com.vishal.vibeplayer.model.Song(
                            title = album.name,
                            artist = album.artist_name,
                            duration = "",
                            path = album.id, // We hide the Album ID here so we can pass it later!
                            isOnline = true,
                            imageUrl = album.image
                        )
                    }

                    // 3. Attach to adapter and handle the click
                    rvTrending.adapter = SquareSongAdapter(albums) { clickedAlbum ->

                        // WHEN CLICKED: Create a Bundle to pass data to the Details Screen
                        val bundle = Bundle().apply {
                            putString("ALBUM_ID", clickedAlbum.path)
                            putString("ALBUM_NAME", clickedAlbum.title)
                            putString("ALBUM_ART", clickedAlbum.imageUrl)
                            putString("ALBUM_ARTIST", clickedAlbum.artist)
                        }

                        // Open PlaylistDetailsFragment and pass the bundle
                        val detailsFragment = PlaylistDetailsFragment()
                        detailsFragment.arguments = bundle

                        // Slide over to the details page (uses the host container!)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.tracks_host_container, detailsFragment)
                            .addToBackStack(null) // Allows the user to press 'Back' to return to the grid
                            .commit()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API_ERROR", "Failed to fetch albums", e)
                    Toast.makeText(requireContext(), "API Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}