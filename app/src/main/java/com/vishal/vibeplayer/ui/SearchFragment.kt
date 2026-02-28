package com.vishal.vibeplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.GenreAdapter
import com.vishal.vibeplayer.adapter.RecentSearchAdapter
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import java.util.concurrent.TimeUnit

class SearchFragment : Fragment() {

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var songAdapter: SongAdapter

    private lateinit var scrollFilters: HorizontalScrollView
    private lateinit var txtRecentSearches: TextView
    private lateinit var rvRecentSearches: RecyclerView
    private lateinit var txtBrowseHeader: TextView
    private lateinit var rvBrowseCategories: RecyclerView

    private val allSongsList = mutableListOf<Song>()
    private val filteredSongsList = mutableListOf<Song>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        etSearch = view.findViewById(R.id.etSearch)

        scrollFilters = view.findViewById(R.id.scrollFilters)
        txtRecentSearches = view.findViewById(R.id.txtRecentSearches)
        rvRecentSearches = view.findViewById(R.id.rvRecentSearches)
        txtBrowseHeader = view.findViewById(R.id.txtBrowseHeader)
        rvBrowseCategories = view.findViewById(R.id.rvBrowseCategories)

        // --- 1. SETUP SEARCH RESULTS ---
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        songAdapter = SongAdapter(filteredSongsList) { clickedSong ->
            // Send the filtered search results as the new queue!
            val index = filteredSongsList.indexOf(clickedSong)
            PlayerManager.startPlaying(requireContext(), filteredSongsList, index)
        }
        rvSearchResults.adapter = songAdapter

        // --- 2. SETUP RECENT SEARCHES ---
        val dummyRecents = listOf("Your Eyes", "Starboy", "Blinding Lights", "Levitating")
        rvRecentSearches.layoutManager = LinearLayoutManager(requireContext())
        rvRecentSearches.adapter = RecentSearchAdapter(dummyRecents)

        // --- 3. SETUP BROWSE GENRES ---
        val dummyGenres = listOf("Pop", "Hip-Hop", "Rock", "Jazz", "Electronic", "Classical")
        rvBrowseCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvBrowseCategories.adapter = GenreAdapter(dummyGenres)

        if (hasStoragePermission()) {
            loadAllSongs()
        }

        // --- 4. HIDE UI WHEN FOCUSED OR TYPING ---

        // Hide UI when they just tap the box (to make room for the keyboard)
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setOtherUiVisibility(View.GONE)
            } else if (etSearch.text.isEmpty()) {
                setOtherUiVisibility(View.VISIBLE)
            }
        }

        // Handle the actual typing logic
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    rvSearchResults.visibility = View.VISIBLE
                    setOtherUiVisibility(View.GONE)
                    filterSongs(query)
                } else {
                    rvSearchResults.visibility = View.GONE
                    // Only show default UI if keyboard is closed/focus lost
                    if (!etSearch.hasFocus()) {
                        setOtherUiVisibility(View.VISIBLE)
                    }
                    filteredSongsList.clear()
                    songAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    private fun setOtherUiVisibility(visibility: Int) {
        scrollFilters.visibility = visibility
        txtRecentSearches.visibility = visibility
        rvRecentSearches.visibility = visibility
        txtBrowseHeader.visibility = visibility
        rvBrowseCategories.visibility = visibility
    }

    private fun filterSongs(query: String) {
        filteredSongsList.clear()
        val lowerCaseQuery = query.lowercase()

        for (song in allSongsList) {
            if (song.title.lowercase().contains(lowerCaseQuery) ||
                song.artist.lowercase().contains(lowerCaseQuery)) {
                filteredSongsList.add(song)
            }
        }
        songAdapter.notifyDataSetChanged()
    }

    private fun loadAllSongs() {
        allSongsList.clear()
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
                allSongsList.add(Song(title, artist, formatTime(durationMs), path))
            }
        }
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
}