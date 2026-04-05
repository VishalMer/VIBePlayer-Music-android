package com.vishal.vibeplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.QuickMixAdapter
import com.vishal.vibeplayer.adapter.SquareSongAdapter
import com.vishal.vibeplayer.database.AppDatabase
import com.vishal.vibeplayer.manager.AppState
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Playlist
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    private var rootView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        // 2. ONLY build the screen and load data if it hasn't been built yet!
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_home, container, false)

            setupGreeting(rootView!!)
            setupModeToggle(rootView!!)
            setupHorizontalLists(rootView!!)

            if (PlayerManager.allSongs.isEmpty()) {
                if (hasStoragePermission()) {
                    // THE FIX: Do not scan the hard drive on the main thread!
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        loadAllSongsIntoBrain()
                        withContext(Dispatchers.Main) {
                            setupLibraryStats(rootView!!)
                            refreshTrackLists()
                        }
                    }
                }
            } else {
                setupLibraryStats(rootView!!)
            }

            loadQuickMixes(rootView!!)
        }
        return rootView
    }

    // 1. Trigger the update every time the Home Tab is opened
    override fun onResume() {
        super.onResume()
        if (PlayerManager.allSongs.isNotEmpty()) {
            refreshTrackLists()
        }
    }

    // 2. The Master Function for Dynamic Home Screen Data
    private fun refreshTrackLists() {
        viewLifecycleOwner.lifecycleScope.launch {

            // WAIT for the navigation pill to finish sliding (Eliminates the lag!)
            delay(150)

            // Do the heavy sorting math in the background
            val recentList = withContext(Dispatchers.Default) {
                var list = PlayerManager.playHistory.take(15)
                if (list.isEmpty()) {
                    list = PlayerManager.allSongs.filter { !it.isOnline }.shuffled().take(15)
                }
                list
            }

            val mostPlayedList = withContext(Dispatchers.Default) {
                var list = PlayerManager.allSongs
                    .filter { PlayerManager.playCounts.containsKey(it.path ?: "") }
                    .sortedByDescending { PlayerManager.playCounts[it.path ?: ""] ?: 0 }
                    .take(15)

                if (list.isEmpty()) {
                    list = PlayerManager.allSongs.filter { !it.isOnline }.shuffled().take(15)
                }
                list
            }

            // Go back to the UI thread to draw the covers
            withContext(Dispatchers.Main) {
                val rvRecent = view?.findViewById<RecyclerView>(R.id.rvRecentPlayed)
                val rvMostPlayed = view?.findViewById<RecyclerView>(R.id.rvMostPlayed)

                rvRecent?.adapter = SquareSongAdapter(recentList) { clickedSong ->
                    val index = recentList.indexOf(clickedSong)
                    PlayerManager.startPlaying(requireContext(), recentList, index)
                }

                rvMostPlayed?.adapter = SquareSongAdapter(mostPlayedList) { clickedSong ->
                    val index = mostPlayedList.indexOf(clickedSong)
                    PlayerManager.startPlaying(requireContext(), mostPlayedList, index)
                }
            }
        }
    }

    private fun setupModeToggle(view: View) {
        val btnToggleMode = view.findViewById<MaterialButton>(R.id.btnToggleMode)
        val prefs = requireContext().getSharedPreferences("VibePrefs", Context.MODE_PRIVATE)

        AppState.isOnlineMode = prefs.getBoolean("IS_ONLINE_MODE", false)
        updateToggleUI(btnToggleMode, AppState.isOnlineMode)

        btnToggleMode.setOnClickListener {
            AppState.isOnlineMode = !AppState.isOnlineMode
            prefs.edit().putBoolean("IS_ONLINE_MODE", AppState.isOnlineMode).apply()
            updateToggleUI(btnToggleMode, AppState.isOnlineMode)

            val modeName = if (AppState.isOnlineMode) "Online Mode" else "Offline Mode"
            Toast.makeText(requireContext(), "Switched to $modeName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadQuickMixes(view: View) {
        val rvQuickMixes = view.findViewById<RecyclerView>(R.id.rvQuickMixes)
        rvQuickMixes.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        CoroutineScope(Dispatchers.IO).launch {
            val databasePlaylists = AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists()

            // THE FIX: Always load favorites from PlayerManager first
            PlayerManager.loadFavorites(requireContext())
            val favCount = PlayerManager.favoriteSongs.size

            val favoritesPlaylist = Playlist(
                id = -1,
                title = "My Favorites",
                subtitle = "$favCount Tracks"
            )

            val mappedPlaylists = databasePlaylists.map { dbItem ->
                Playlist(
                    id = dbItem.id,
                    title = dbItem.name,
                    subtitle = "Custom Mix"
                )
            }

            // Always add Favorites first, then the custom ones
            val allMixes = mutableListOf<Playlist>()
            allMixes.add(favoritesPlaylist)
            allMixes.addAll(mappedPlaylists)

            withContext(Dispatchers.Main) {
                rvQuickMixes.adapter = QuickMixAdapter(allMixes) { clickedPlaylist ->
                    // Notice we are using NavController here instead of FragmentManager for consistency with the rest of your app!
                    val bundle = Bundle().apply {
                        putString("PLAYLIST_NAME", clickedPlaylist.title)
                        putInt("CUSTOM_PLAYLIST_ID", clickedPlaylist.id)
                    }
                    findNavController().navigate(R.id.playlistDetailsFragment, bundle)
                }
            }
        }
    }

    private fun updateToggleUI(btnToggleMode: MaterialButton, isOnline: Boolean) {
        if (isOnline) {
            btnToggleMode.setIconResource(R.drawable.ic_online_cloud)
            btnToggleMode.backgroundTintList = null
            btnToggleMode.setBackgroundResource(R.drawable.bg_chip_outline)
        } else {
            btnToggleMode.setIconResource(R.drawable.ic_offline_cloud)
            btnToggleMode.backgroundTintList = null

            val offlineBg = android.graphics.drawable.GradientDrawable()
            offlineBg.setColor(android.graphics.Color.parseColor("#33FFFFFF"))
            offlineBg.cornerRadius = 100f
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
        val rvRecentPlayed = view.findViewById<RecyclerView>(R.id.rvRecentPlayed)
        val rvMostPlayed = view.findViewById<RecyclerView>(R.id.rvMostPlayed)
        val rvQuickMixes = view.findViewById<RecyclerView>(R.id.rvQuickMixes)

        rvRecentPlayed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvMostPlayed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvQuickMixes.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupLibraryStats(view: View) {
        val totalTracks = PlayerManager.allSongs.size
        val totalArtists = PlayerManager.allSongs.map { it.artist }.distinct().size
        val totalFavorites = PlayerManager.favoriteSongs.size

        try {
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
            incLibPlaylists.findViewById<TextView>(R.id.txtStatValue).text = "1"
            incLibPlaylists.findViewById<TextView>(R.id.txtStatName).text = "Custom Playlists"

            val mockLast7Tracks = if (totalTracks > 10) totalTracks / 4 else totalTracks
            val mockLast7Mins = mockLast7Tracks * 3

            val incLast7Tracks = view.findViewById<View>(R.id.incLast7Tracks)
            incLast7Tracks.findViewById<TextView>(R.id.txtStatValue).text = mockLast7Tracks.toString()
            incLast7Tracks.findViewById<TextView>(R.id.txtStatName).text = "Tracks Played"

            val incLast7Time = view.findViewById<View>(R.id.incLast7Time)
            incLast7Time.findViewById<TextView>(R.id.txtStatValue).text = formatMinutesToHours(mockLast7Mins)
            incLast7Time.findViewById<TextView>(R.id.txtStatName).text = "Time Listened"

            val incLifeTracks = view.findViewById<View>(R.id.incLifeTracks)
            incLifeTracks.findViewById<TextView>(R.id.txtStatValue).text = (totalTracks * 2).toString()
            incLifeTracks.findViewById<TextView>(R.id.txtStatName).text = "Tracks Played"

            val mockLifeTimeMins = totalTracks * 6
            val incLifeTime = view.findViewById<View>(R.id.incLifeTime)
            incLifeTime.findViewById<TextView>(R.id.txtStatValue).text = formatMinutesToHours(mockLifeTimeMins)
            incLifeTime.findViewById<TextView>(R.id.txtStatName).text = "Time Listened"

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAllSongsIntoBrain() {
        val tempMasterList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = requireContext().contentResolver.query(uri, projection, selection, null, sortOrder)

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
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun formatMinutesToHours(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}