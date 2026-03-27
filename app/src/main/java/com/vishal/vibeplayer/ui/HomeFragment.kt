package com.vishal.vibeplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import android.content.Context
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import com.vishal.vibeplayer.manager.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.vishal.vibeplayer.model.Playlist
import com.vishal.vibeplayer.adapter.QuickMixAdapter
import com.vishal.vibeplayer.database.AppDatabase


class HomeFragment : Fragment() {

    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        if (rootView == null) {
            // Inflate it for the first time
            rootView = inflater.inflate(R.layout.fragment_home, container, false)

            // 1. Dynamic Greeting (Pass rootView!! instead of view)
            setupGreeting(rootView!!)

            setupModeToggle(rootView!!)

            // --- THE SAFETY NET ---
            if (PlayerManager.allSongs.isEmpty()) {
                if (hasStoragePermission()) {
                    loadAllSongsIntoBrain()
                }
            }

            // 2. Setup Horizontal Recycler Views
            setupHorizontalLists(rootView!!)

            loadQuickMixes(rootView!!)

            // 3. Calculate and display Library Stats
            setupLibraryStats(rootView!!)
        }
        return rootView
    }

    // 1. Override onResume so it updates every time user opens the Home tab
    override fun onResume() {
        super.onResume()
        refreshRecentTracks()
    }

    private fun refreshRecentTracks() {
        // 2. Find your RecyclerView
        val rvRecent = view?.findViewById<RecyclerView>(R.id.rvRecentPlayed)

        // 3. Take the top 15 most recent
        var displayList = PlayerManager.playHistory.take(15)

        // 4. PRO UX TRICK: If history is empty (like on a fresh app launch),
        // fallback to displaying 15 random offline tracks so the UI never looks empty or broken!
        if (displayList.isEmpty()) {
            displayList = PlayerManager.allSongs.filter { !it.isOnline }.shuffled().take(15)
        }

        // 5. Attach the list to your Horizontal Adapter
        // NOTE: Change 'YourHorizontalAdapterName' to whatever adapter you are using for those square cards!
        rvRecent?.adapter = SquareSongAdapter(displayList) { clickedSong ->

            // 6. When a user clicks a square card, play it immediately!
            val index = displayList.indexOf(clickedSong)
            PlayerManager.startPlaying(requireContext(), displayList, index)

        }
    }
    private fun setupModeToggle(view: View) {
        val btnToggleMode = view.findViewById<MaterialButton>(R.id.btnToggleMode)
        val prefs = requireContext().getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)

        // 1. Sync global AppState with SharedPreferences on startup
        AppState.isOnlineMode = prefs.getBoolean("IS_ONLINE_MODE", false)
        updateToggleUI(btnToggleMode, AppState.isOnlineMode)

        // 2. Listen for clicks to switch modes!
        btnToggleMode.setOnClickListener {
            // Flip the global state!
            AppState.isOnlineMode = !AppState.isOnlineMode

            // Save the new state to device memory so it remembers next time
            prefs.edit().putBoolean("IS_ONLINE_MODE", AppState.isOnlineMode).apply()

            // Update the button visuals
            updateToggleUI(btnToggleMode, AppState.isOnlineMode)

            // Show a toast so the user knows
            val modeName = if (AppState.isOnlineMode) "Online Mode" else "Offline Mode"
            Toast.makeText(requireContext(), "Switched to $modeName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadQuickMixes(view: View) {
        val rvQuickMixes = view.findViewById<RecyclerView>(R.id.rvQuickMixes)
        rvQuickMixes.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Fetch user playlists from your Room Database
            // (Adjust "PlaylistDatabase.getDatabase..." to match your actual database call)
            val databasePlaylists = AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists()

            // 2. Generate the "Favorites" playlist dynamically!
            // We match the saved favorite paths with the actual Song objects in PlayerManager
            // 2. Generate the "Favorites" playlist dynamically!
            val favoriteSongsList = PlayerManager.allSongs.filter { PlayerManager.favoriteSongs.contains(it.path) }

            // 1. Add the ID to the Favorites Playlist
            val favoritesPlaylist = Playlist(
                id = -1, // Favorites doesn't have a DB ID, so we use -1
                title = "Favorites",
                subtitle = "${favoriteSongsList.size} Tracks"
            )

            // 2. Add the ID to the mapped Database Playlists
            val mappedPlaylists = databasePlaylists.map { dbItem ->
                Playlist(
                    id = dbItem.id,     // Pass the database ID!
                    title = dbItem.name, // Pass the database name!
                    subtitle = "Custom Mix"
                )
            }

            val allMixes = mutableListOf<Playlist>()
            if (favoriteSongsList.isNotEmpty()) {
                allMixes.add(favoritesPlaylist)
            }
            allMixes.addAll(mappedPlaylists)

            // 3. Send the exact arguments the Details Fragment is looking for!
            withContext(Dispatchers.Main) {
                rvQuickMixes.adapter = QuickMixAdapter(allMixes) { clickedPlaylist ->

                    val fragment = PlaylistDetailsFragment().apply {
                        arguments = android.os.Bundle().apply {
                            // Give the Details Fragment EXACTLY what it wants:
                            putString("PLAYLIST_NAME", clickedPlaylist.title)
                            putInt("CUSTOM_PLAYLIST_ID", clickedPlaylist.id)
                        }
                    }

                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
    }

    private fun updateToggleUI(btnToggleMode: MaterialButton, isOnline: Boolean) {
        if (isOnline) {
            // ONLINE MODE
            btnToggleMode.setIconResource(R.drawable.ic_online_cloud)
            btnToggleMode.backgroundTintList = null
            btnToggleMode.setBackgroundResource(R.drawable.bg_chip_outline)

        } else {
            // OFFLINE MODE
            btnToggleMode.setIconResource(R.drawable.ic_offline_cloud)
            btnToggleMode.backgroundTintList = null

            // Draw a perfect, semi-transparent glass circle programmatically!
            val offlineBg = android.graphics.drawable.GradientDrawable()
            offlineBg.setColor(android.graphics.Color.parseColor("#33FFFFFF"))
            offlineBg.cornerRadius = 100f // Keeps it perfectly round
            btnToggleMode.background = offlineBg
        }
    }

    private fun setupGreeting(view: View) {
        val txtGreeting = view.findViewById<TextView>(R.id.txtGreeting)
        val calendar = Calendar.getInstance()

        val greeting = when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }

        txtGreeting.text = greeting
    }

    private fun setupHorizontalLists(view: View) {
        // We are keeping your original XML IDs but changing how they are used!
        val rvRecentPlayed =
            view.findViewById<RecyclerView>(R.id.rvRecentPlayed) // Now acts as Recent Played
        val rvMostPlayed =
            view.findViewById<RecyclerView>(R.id.rvMostPlayed)      // Now acts as Most Played
        val rvQuickMixes = view.findViewById<RecyclerView>(R.id.rvQuickMixes)

        rvRecentPlayed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMostPlayed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvQuickMixes.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        if (PlayerManager.allSongs.isNotEmpty()) {
            // 1. Recent Played Tracks (Using Square Adapter)
            val mockRecentPlayed = PlayerManager.allSongs.shuffled().take(8)
            rvRecentPlayed.adapter = SquareSongAdapter(mockRecentPlayed) { clickedSong ->
                val index = mockRecentPlayed.indexOf(clickedSong)
                PlayerManager.startPlaying(requireContext(), mockRecentPlayed, index)
            }

            // 2. Most Played Tracks (Using Square Adapter)
            val mockMostPlayed = PlayerManager.allSongs.shuffled().take(8)
            rvMostPlayed.adapter = SquareSongAdapter(mockMostPlayed) { clickedSong ->
                val index = mockMostPlayed.indexOf(clickedSong)
                PlayerManager.startPlaying(requireContext(), mockMostPlayed, index)
            }

            // 3. Quick Mixes (Left empty as requested!)
            rvQuickMixes.adapter = null
        }
    }


    private fun setupLibraryStats(view: View) {
        val totalTracks = PlayerManager.allSongs.size
        val totalArtists = PlayerManager.allSongs.map { it.artist }.distinct().size
        val totalFavorites = PlayerManager.favoriteSongs.size

        try {
            // --- 1. REAL LIBRARY STATS ---
            val incLibTracks = view.findViewById<View>(R.id.incLibTracks)
            incLibTracks.findViewById<TextView>(R.id.txtStatValue).text = totalTracks.toString()
            incLibTracks.findViewById<TextView>(R.id.txtStatName).text = "Total Tracks"

            val incLibArtists = view.findViewById<View>(R.id.incLibArtists)
            incLibArtists.findViewById<TextView>(R.id.txtStatValue).text = totalArtists.toString()
            incLibArtists.findViewById<TextView>(R.id.txtStatName).text = "Unique Artists"

            val incLibFavs = view.findViewById<View>(R.id.incLibFavs)
            incLibFavs.findViewById<TextView>(R.id.txtStatValue).text = totalFavorites.toString()
            incLibFavs.findViewById<TextView>(R.id.txtStatName).text = "Favorite Songs"

            val incLibPlaylists = view.findViewById<View>(R.id.incLibPlaylists)
            incLibPlaylists.findViewById<TextView>(R.id.txtStatValue).text =
                "1" // Just 'Liked Songs' for now!
            incLibPlaylists.findViewById<TextView>(R.id.txtStatName).text = "Custom Playlists"

            // --- 2. MOCK PLAYBACK STATS (Until we build a database!) ---
            val mockLast7Tracks = if (totalTracks > 10) totalTracks / 4 else totalTracks
            val mockLast7Mins = mockLast7Tracks * 3 // Assume ~3 mins per track

            val incLast7Tracks = view.findViewById<View>(R.id.incLast7Tracks)
            incLast7Tracks.findViewById<TextView>(R.id.txtStatValue).text = mockLast7Tracks.toString()
            incLast7Tracks.findViewById<TextView>(R.id.txtStatName).text = "Tracks Played"

            val incLast7Time = view.findViewById<View>(R.id.incLast7Time)
            // UPDATE HERE: Use the new formatter!
            incLast7Time.findViewById<TextView>(R.id.txtStatValue).text = formatMinutesToHours(mockLast7Mins)
            incLast7Time.findViewById<TextView>(R.id.txtStatName).text = "Time Listened"

            val incLifeTracks = view.findViewById<View>(R.id.incLifeTracks)
            incLifeTracks.findViewById<TextView>(R.id.txtStatValue).text = (totalTracks * 2).toString()
            incLifeTracks.findViewById<TextView>(R.id.txtStatName).text = "Tracks Played"

            val mockLifeTimeMins = totalTracks * 6
            val incLifeTime = view.findViewById<View>(R.id.incLifeTime)
            // UPDATE HERE: Use the new formatter!
            incLifeTime.findViewById<TextView>(R.id.txtStatValue).text = formatMinutesToHours(mockLifeTimeMins)
            incLifeTime.findViewById<TextView>(R.id.txtStatName).text = "Time Listened"

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    // --- THE SCANNER LOGIC ---
    private fun loadAllSongsIntoBrain() {
        val tempMasterList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor =
            requireContext().contentResolver.query(uri, projection, selection, null, sortOrder)

        cursor?.use {
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val title = it.getString(titleCol) ?: "Unknown"
                val artist = it.getString(artistCol) ?: "Unknown"
                val durationMs = it.getLong(durationCol)
                val path = it.getString(dataCol)
                tempMasterList.add(Song(title, artist, formatTime(durationMs), path))
            }
        }

        PlayerManager.allSongs = tempMasterList
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun formatMinutesToHours(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}