package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.GenreAdapter
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1. Removed (R.layout...) from the constructor so we can cache it manually!
class OnlineTracksFragment : Fragment() {

    // TODO: Replace with your actual Jamendo API Client ID!
    private val JAMENDO_CLIENT_ID = "1287b878"

    // 2. Create a variable to hold the layout in memory
    private var rootView: View? = null

    // 3. Use onCreateView instead of onViewCreated
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // 4. Only build the view and call the internet API if it doesn't exist yet!
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_online_tracks, container, false)

            // Notice the !! to tell Kotlin we know rootView isn't null here
            val rvTrending = rootView!!.findViewById<RecyclerView>(R.id.rvTrendingTracks)
            val rvMoods = rootView!!.findViewById<RecyclerView>(R.id.rvMoods)

            setupMoodGrid(rvMoods)

            // This heavy network call now only happens ONCE!
            fetchTrendingTracks(rvTrending)
        }

        // 5. Instantly return the cached view!
        return rootView
    }

    private fun setupMoodGrid(rvMoods: RecyclerView) {
        val moods = listOf("Chill", "Workout", "Romantic", "Party", "Focus", "Pop")
        rvMoods.adapter = GenreAdapter(moods)
    }

    private fun fetchTrendingTracks(rvTrending: RecyclerView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch ALBUMS instead of single tracks!
                val response = RetrofitClient.getApiService(requireContext()).getTrendingAlbums(clientId = JAMENDO_CLIENT_ID)
                withContext(Dispatchers.Main) {

                    // Map the Album data to your Song model so the Square adapter can render it
                    val albums = response.results.map { album ->
                        com.vishal.vibeplayer.model.Song(
                            title = album.name,
                            artist = album.artist_name,
                            duration = "",
                            path = album.id,
                            isOnline = true,
                            imageUrl = album.image
                        )
                    }

                    // Attach to adapter and handle the click
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

                        // Slide over to the details page
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.tracks_host_container, detailsFragment)
                            .addToBackStack(null)
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