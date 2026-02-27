package com.vishal.vibeplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import java.util.concurrent.TimeUnit

class AllTracksFragment : Fragment() {

    private lateinit var rvAllTracks: RecyclerView
    private val songList = mutableListOf<Song>()

    // 1. The Permission Asker (Pops up the "Allow Access" dialog)
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
        val view = inflater.inflate(R.layout.fragment_all_tracks, container, false)
        rvAllTracks = view.findViewById(R.id.rvAllTracks)
        rvAllTracks.layoutManager = LinearLayoutManager(requireContext())

        // 2. Check for permissions as soon as the screen opens
        checkPermissions()

        return view
    }

    private fun checkPermissions() {
        // Android 13+ uses a different permission name than older Androids
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            // We already have permission! Go ahead and scan the phone.
            loadRealSongs()
        } else {
            // We don't have permission yet. Pop up the dialog!
            permissionLauncher.launch(permission)
        }
    }

    // 3. The MediaStore Scanner
    private fun loadRealSongs() {
        songList.clear()

        // Ask Android's database to give us Audio files
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA // This is the actual file path!
        )

        // Filter out ringtones and alarms; we only want actual music!
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // Sort alphabetically by title
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = requireContext().contentResolver.query(uri, projection, selection, null, sortOrder)

        cursor?.use {
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            // Loop through every single audio file found on the phone
            while (it.moveToNext()) {
                val title = it.getString(titleCol) ?: "Unknown Title"
                val artist = it.getString(artistCol) ?: "Unknown Artist"
                val durationMs = it.getLong(durationCol)
                val path = it.getString(dataCol)

                val formattedDuration = formatTime(durationMs)

                // Add the real file to our list!
                songList.add(Song(title, artist, formattedDuration, path))
            }
        }

        // 4. Send the massive list of real songs into your custom Adapter!
        rvAllTracks.adapter = SongAdapter(songList) { clickedSong ->
            // FIX APPLIED HERE: Added requireContext() so the Global Brain can launch the Service!
            PlayerManager.initializeAndPlay(requireContext(), clickedSong)
        }
    }

    // Math helper to turn milliseconds into "3:45"
    private fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}