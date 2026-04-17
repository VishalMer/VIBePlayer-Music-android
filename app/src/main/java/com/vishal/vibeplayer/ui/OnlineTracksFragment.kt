package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class OnlineTracksFragment : Fragment() {

    private val JAMENDO_CLIENT_ID = "1287b878"
    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_online_tracks, container, false)

            setupDynamicGreeting(rootView!!)
            setupRecentlyPlayed(rootView!!)
            fetchAllOnlineData(rootView!!)
        }
        return rootView
    }

    // ==========================================
    // 1. DYNAMIC GREETING
    // ==========================================
    private fun setupDynamicGreeting(view: View) {
        val txtGreeting = view.findViewById<TextView>(R.id.txtGreeting)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        txtGreeting.text = when (hour) {
            in 5..11 -> "Good Morning!"
            in 12..16 -> "Good Afternoon!"
            in 17..20 -> "Good Evening!"
            else -> "Good Night!"
        }
    }

    // ==========================================
    // 2. TRUE RECENTLY PLAYED FIX
    // ==========================================
    private fun setupRecentlyPlayed(view: View) {
        val rvRecentlyPlayed = view.findViewById<RecyclerView>(R.id.rvRecentlyPlayed)

        // THE FIX: Grab your actual recent history list instead of sorting by play counts!
        // NOTE: If your Home Screen uses a different variable name (like 'historyList'), change it here!
        var recentList = PlayerManager.playHistory.take(10)

        // Fallback: If history is empty, show standard local tracks
        if (recentList.isEmpty()) {
            recentList = PlayerManager.allSongs.take(10)
        }

        rvRecentlyPlayed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvRecentlyPlayed.adapter = SquareSongAdapter(recentList) { clickedSong ->
            val index = recentList.indexOf(clickedSong)
            PlayerManager.startPlaying(requireContext(), recentList, index)
        }
    }

    // ==========================================
    // 3. ONLINE DATA MANAGER
    // ==========================================
    private fun fetchAllOnlineData(view: View) {
        val rvTrending = view.findViewById<RecyclerView>(R.id.rvTrendingTracks)
        val rvTopArtists = view.findViewById<RecyclerView>(R.id.rvTopArtists)
        val rvNewReleases = view.findViewById<RecyclerView>(R.id.rvNewReleases)

        val cardHeroBanner = view.findViewById<CardView>(R.id.cardHeroBanner)
        val imgHeroBackground = view.findViewById<ImageView>(R.id.imgHeroBackground)
        val txtHeroTitle = view.findViewById<TextView>(R.id.txtHeroTitle)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getApiService(requireContext()).getTrendingAlbums(clientId = JAMENDO_CLIENT_ID)

                withContext(Dispatchers.Main) {
                    val allAlbums = response.results.map { album ->
                        com.vishal.vibeplayer.model.Song(
                            title = album.name,
                            artist = album.artist_name,
                            duration = "",
                            path = album.id, // We store the Album ID here!
                            isOnline = true,
                            imageUrl = album.image
                        )
                    }

                    if (allAlbums.isNotEmpty()) {

                        // --- HERO BANNER ---
                        val heroAlbum = allAlbums[0]
                        txtHeroTitle.text = heroAlbum.title
                        Glide.with(this@OnlineTracksFragment).load(heroAlbum.imageUrl).into(imgHeroBackground)
                        cardHeroBanner.setOnClickListener { handleAlbumClick(heroAlbum) }

                        // --- TRENDING TRACKS ---
                        rvTrending.adapter = SquareSongAdapter(allAlbums.drop(1).take(10)) { handleAlbumClick(it) }

                        // --- TOP ARTISTS ---
                        val uniqueArtists = allAlbums.distinctBy { it.artist }.take(10)
                        rvTopArtists.adapter = SquareSongAdapter(uniqueArtists) { handleAlbumClick(it) }

                        // --- NEW RELEASES ---
                        rvNewReleases.adapter = SquareSongAdapter(allAlbums.shuffled().take(10)) { handleAlbumClick(it) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API_ERROR", "Failed to fetch online data", e)
                    Toast.makeText(requireContext(), "API Error: Check Internet", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // 4. SMART ALBUM CLICK LOGIC
    // ==========================================
    private fun handleAlbumClick(album: com.vishal.vibeplayer.model.Song) {

        // Show a tiny toast so the user knows it's loading
        Toast.makeText(requireContext(), "Loading Album...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fetch the tracks using the endpoint we verified above
                val tracksResponse = RetrofitClient.getApiService(requireContext())
                    .getTracksByAlbum(clientId = JAMENDO_CLIENT_ID, albumId = album.path ?: "")

                withContext(Dispatchers.Main) {
                    val tracks = tracksResponse.results

                    // 2. CHECK THE SIZE!
                    if (tracks.size == 1) {
                        // SMART PLAY: It's a single! Play it instantly.
                        val singleTrack = tracks[0]

                        // We now use your safely renamed variables!
                        val songToPlay = com.vishal.vibeplayer.model.Song(
                            title = singleTrack.title ?: "Unknown Track",
                            artist = singleTrack.artist ?: "Unknown Artist",
                            duration = singleTrack.duration?.toString() ?: "0",
                            path = singleTrack.audioUrl ?: "", // Added the Elvis operator fallback!
                            isOnline = true,
                            imageUrl = singleTrack.imageUrl // Using imageUrl
                        )

                        PlayerManager.startPlaying(requireContext(), listOf(songToPlay), 0)

                    } else {
                        // It's a full album. Open the details page normally.
                        openAlbumDetails(album)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Safe Fallback: If the network check fails, just open the details page
                    openAlbumDetails(album)
                }
            }
        }
    }

    private fun openAlbumDetails(album: com.vishal.vibeplayer.model.Song) {
        val bundle = Bundle().apply {
            putString("ALBUM_ID", album.path)
            putString("ALBUM_NAME", album.title)
            putString("ALBUM_ART", album.imageUrl)
            putString("ALBUM_ARTIST", album.artist)
        }

        val detailsFragment = PlaylistDetailsFragment()
        detailsFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.tracks_host_container, detailsFragment)
            .addToBackStack(null)
            .commit()
    }
}