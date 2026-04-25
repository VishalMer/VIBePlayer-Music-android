package com.vishal.vibeplayer.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.database.AppDatabase
import com.vishal.vibeplayer.database.PlaylistSongEntity
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LocalTracksFragment : Fragment() {

    private var rootView: View? = null

    private lateinit var rvAllTracks: RecyclerView
    private val songList = mutableListOf<Song>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadRealSongs()
        } else {
            Toast.makeText(requireContext(), "Permission denied! Can't load music.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_local_tracks, container, false)

            rvAllTracks = rootView!!.findViewById(R.id.rvAllTracks)
            rvAllTracks.layoutManager = LinearLayoutManager(requireContext())

            checkPermissions()
        }

        return rootView
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadRealSongs()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadRealSongs() {
        songList.clear()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
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
                val title = it.getString(titleCol) ?: "Unknown Title"
                val artist = it.getString(artistCol) ?: "Unknown Artist"
                val durationMs = it.getLong(durationCol)
                val path = it.getString(dataCol)

                val formattedDuration = formatTime(durationMs)
                songList.add(Song(title, artist, formattedDuration, path))
            }
        }

        PlayerManager.allSongs = songList

        rvAllTracks.adapter = SongAdapter(
            songs = songList,
            onSongClicked = { clickedSong ->
                // ==========================================
                // SURGICAL FIX 1: Match by Title and Artist instead of Path
                // This prevents Android's null-path bug from ignoring your clicks!
                // ==========================================
                val index = songList.indexOfFirst { it.title == clickedSong.title && it.artist == clickedSong.artist }

                if (index != -1) {
                    PlayerManager.startPlaying(requireContext(), songList, index)
                } else {
                    Toast.makeText(requireContext(), "Error: Song not found in list", Toast.LENGTH_SHORT).show()
                }
            },
            onMoreOptionsClicked = { clickedSong ->
                showSongOptionsBottomSheet(clickedSong)
            }
        )
    }

    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // --- BOTTOM SHEET LOGIC ---
    private fun showSongOptionsBottomSheet(song: Song) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())

        // SURGICAL FIX 2: Use View.inflate to prevent silent crashes
        val view = View.inflate(requireContext(), R.layout.bottom_sheet_song_options, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.bsSongTitle).text = song.title
        view.findViewById<TextView>(R.id.bsSongArtist).text = song.artist

        val isFav = PlayerManager.favoriteSongs.contains(song.path)
        val iconFav = view.findViewById<ImageView>(R.id.bsIconFavorite)
        val textFav = view.findViewById<TextView>(R.id.bsTextFavorite)

        if (isFav) {
            iconFav.setImageResource(R.drawable.ic_heart_fill)
            textFav.text = "Remove from Favorites"
        } else {
            iconFav.setImageResource(R.drawable.ic_heart)
            textFav.text = "Add to Favorites"
        }

        // ==========================================
        // NEW: Play Now
        // ==========================================
        view.findViewById<View>(R.id.bsOptionPlayNow).setOnClickListener {
            bottomSheetDialog.dismiss()
            PlayerManager.startPlaying(requireContext(), listOf(song), 0)
        }

        // ==========================================
        // NEW: Play Next
        // ==========================================
        view.findViewById<View>(R.id.bsOptionPlayNext).setOnClickListener {
            bottomSheetDialog.dismiss()
            PlayerManager.insertNextInQueue(song) // Renamed!
            Toast.makeText(requireContext(), "Playing next", Toast.LENGTH_SHORT).show()
        }

        // ==========================================
        // NEW: Add to Queue
        // ==========================================
        view.findViewById<View>(R.id.bsOptionAddToQueue).setOnClickListener {
            bottomSheetDialog.dismiss()
            PlayerManager.appendSongToQueue(song) // Renamed!
            Toast.makeText(requireContext(), "Added to queue", Toast.LENGTH_SHORT).show()
        }

        // ==========================================
        // NEW: Properties
        // ==========================================
        view.findViewById<View>(R.id.bsOptionProperties).setOnClickListener {
            bottomSheetDialog.dismiss()
            showPropertiesDialog(song)
        }

        // ==========================================
        // EXISTING LOGIC
        // ==========================================
        view.findViewById<View>(R.id.bsOptionAddToPlaylist).setOnClickListener {
            bottomSheetDialog.dismiss()
            showSelectPlaylistDialog(song) // Ensure this function exists in your fragment!
        }

        view.findViewById<View>(R.id.bsOptionFavorite).setOnClickListener {
            val temp = PlayerManager.currentSong
            PlayerManager.currentSong = song
            PlayerManager.toggleFavorite(requireContext())
            PlayerManager.currentSong = temp

            Toast.makeText(requireContext(), "Favorites Updated", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }

        view.findViewById<View>(R.id.bsOptionShare).setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title} by ${song.artist} on VibePlayer!")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showPropertiesDialog(song: Song) {
        // Dynamically calculate size using the file path!
        val sizeInMb = try {
            val file = java.io.File(song.path ?: "")
            if (file.exists()) {
                String.format(java.util.Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
            } else {
                "Unknown Size"
            }
        } catch (e: Exception) {
            "Unknown Size"
        }

        val details = """
        Title: ${song.title}
        Artist: ${song.artist}
        Size: $sizeInMb
        
        Path:
        ${song.path}
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Song Properties")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    // --- DATABASE PLAYLIST SELECTOR ---
    private fun showSelectPlaylistDialog(song: Song) {
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val playlists = db.playlistDao().getAllPlaylists()

            withContext(Dispatchers.Main) {
                if (playlists.isEmpty()) {
                    Toast.makeText(requireContext(), "No custom playlists found. Create one first!", Toast.LENGTH_LONG).show()
                    return@withContext
                }

                val playlistNames = playlists.map { it.name }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Add to...")
                    .setItems(playlistNames) { _, which ->
                        val selectedPlaylist = playlists[which]

                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val existingSongs = db.playlistDao().getSongsInPlaylist(selectedPlaylist.id)
                            val isAlreadyAdded = existingSongs.any { it.songPath == song.path }

                            if (isAlreadyAdded) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Already added to ${selectedPlaylist.name}!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val newEntry = PlaylistSongEntity(
                                    playlistId = selectedPlaylist.id,
                                    songPath = song.path ?: "",
                                    isOnline = song.isOnline
                                )
                                db.playlistDao().insertSongIntoPlaylist(newEntry)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Added to ${selectedPlaylist.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .show()
            }
        }
    }
}