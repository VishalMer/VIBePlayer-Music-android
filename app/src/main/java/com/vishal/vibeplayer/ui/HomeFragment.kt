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

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // 1. Dynamic Greeting
        setupGreeting(view)

        // --- THE SAFETY NET ---
        // Because Home is the first screen, we MUST check if the Brain is empty!
        if (PlayerManager.allSongs.isEmpty()) {
            if (hasStoragePermission()) {
                loadAllSongsIntoBrain()
            }
        }

        // 2. Setup Horizontal Recycler Views (Now that we have data!)
        setupHorizontalLists(view)

        // 3. Calculate and display Library Stats
        setupLibraryStats(view)

        return view
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
            view.findViewById<RecyclerView>(R.id.rvArtists)      // Now acts as Most Played
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