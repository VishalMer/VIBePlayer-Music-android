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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val filteredSongsList = mutableListOf<Song>()

    // --- NEW: Coroutine Scope for background processing! ---
    private val searchScope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        etSearch = view.findViewById(R.id.etSearch)

        scrollFilters = view.findViewById(R.id.scrollFilters)
        txtRecentSearches = view.findViewById(R.id.txtRecentSearches)
        rvRecentSearches = view.findViewById(R.id.rvRecentSearches)
        txtBrowseHeader = view.findViewById(R.id.txtBrowseHeader)
        rvBrowseCategories = view.findViewById(R.id.rvBrowseCategories)

        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        songAdapter = SongAdapter(filteredSongsList) { clickedSong ->
            val index = filteredSongsList.indexOf(clickedSong)
            PlayerManager.startPlaying(requireContext(), filteredSongsList, index)

            etSearch.clearFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        }
        rvSearchResults.adapter = songAdapter

        val dummyRecents = listOf("Your Eyes", "Starboy", "Blinding Lights", "Levitating")
        rvRecentSearches.layoutManager = LinearLayoutManager(requireContext())
        rvRecentSearches.adapter = RecentSearchAdapter(dummyRecents)

        val dummyGenres = listOf("Pop", "Hip-Hop", "Rock", "Jazz", "Electronic", "Classical")
        rvBrowseCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvBrowseCategories.adapter = GenreAdapter(dummyGenres)

        if (PlayerManager.allSongs.isEmpty()) {
            if (hasStoragePermission()) {
                loadAllSongsIntoBrain()
            }
        }

        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setOtherUiVisibility(View.GONE)
            } else if (etSearch.text.isEmpty()) {
                setOtherUiVisibility(View.VISIBLE)
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()

                // Cancel any ongoing search immediately if the user types another letter
                searchJob?.cancel()

                if (query.isNotEmpty()) {
                    rvSearchResults.visibility = View.VISIBLE
                    setOtherUiVisibility(View.GONE)

                    // Launch the search safely!
                    searchJob = searchScope.launch {
                        delay(300) // Wait 300ms for them to stop typing
                        filterSongs(query)
                    }

                } else {
                    rvSearchResults.visibility = View.GONE
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

    // --- THE NEW ULTRA-FAST SEARCH LOGIC ---
    private suspend fun filterSongs(query: String) {
        // 1. Jump to a background thread so the UI never freezes
        val results = withContext(Dispatchers.Default) {
            PlayerManager.allSongs.filter { song ->
                // 2. Use ignoreCase = true instead of .lowercase() to save tons of memory
                val titleMatches = song.title.startsWith(query, ignoreCase = true) || song.title.contains(" $query", ignoreCase = true)
                val artistMatches = song.artist.startsWith(query, ignoreCase = true) || song.artist.contains(" $query", ignoreCase = true)

                titleMatches || artistMatches
            }
        }

        // 3. Jump back to the Main thread to update the screen!
        withContext(Dispatchers.Main) {
            filteredSongsList.clear()
            filteredSongsList.addAll(results)
            songAdapter.notifyDataSetChanged()
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up the coroutines when you leave the screen to save battery!
        searchScope.cancel()
    }
}