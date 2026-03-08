package com.vishal.vibeplayer.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.database.AppDatabase
import com.vishal.vibeplayer.database.PlaylistSongEntity
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Song
import com.vishal.vibeplayer.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailsFragment : Fragment() {

    // TODO: Replace with your actual Jamendo API Client ID!
    private val JAMENDO_CLIENT_ID = "1287b878"
    private var displaySongs: List<Song> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlist_details, container, false)

        val txtTitle = view.findViewById<TextView>(R.id.txtDetailTitle)
        val txtSubtitle = view.findViewById<TextView>(R.id.txtDetailSubtitle)
        val rvPlaylistSongs = view.findViewById<RecyclerView>(R.id.rvPlaylistSongs)
        val btnBack = view.findViewById<View>(R.id.btnBackPlaylist)
        val btnPlayAll = view.findViewById<View>(R.id.btnPlayAll)
        val ivCover = view.findViewById<ImageView>(R.id.ivPlaylistCover)

        rvPlaylistSongs?.layoutManager = LinearLayoutManager(requireContext())

        val playlistName = arguments?.getString("PLAYLIST_NAME")
        val albumId = arguments?.getString("ALBUM_ID")
        val albumName = arguments?.getString("ALBUM_NAME")
        val albumArt = arguments?.getString("ALBUM_ART")
        val albumArtist = arguments?.getString("ALBUM_ARTIST")
        val customPlaylistId = arguments?.getInt("CUSTOM_PLAYLIST_ID", -1) ?: -1

        if (albumId != null) {
            // ==========================================
            // SCENARIO 1: ONLINE JAMENDO ALBUM
            // ==========================================
            txtTitle?.text = albumName ?: "Unknown Album"
            txtSubtitle?.text = albumArtist ?: "Unknown Artist"

            if (ivCover != null && albumArt != null) {
                Glide.with(this)
                    .load(albumArt)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivCover)
            }
            fetchAlbumTracks(albumId, rvPlaylistSongs, albumArt ?: "", txtSubtitle, albumArtist)

        } else if (customPlaylistId != -1) {
            // ==========================================
            // SCENARIO 2: CUSTOM ROOM DATABASE PLAYLIST
            // ==========================================
            txtTitle?.text = playlistName ?: "Custom Playlist"
            fetchCustomPlaylistSongs(customPlaylistId, rvPlaylistSongs, txtSubtitle)

        } else {
            // ==========================================
            // SCENARIO 3: OFFLINE FAVORITES PLAYLIST
            // ==========================================
            txtTitle?.text = playlistName ?: "Unknown Playlist"

            if (playlistName == "Liked Songs" || playlistName == "My Favorites") {
                PlayerManager.loadFavorites(requireContext())
                val allSongsOnDevice = PlayerManager.allSongs

                displaySongs = allSongsOnDevice.filter { song ->
                    PlayerManager.favoriteSongs.contains(song.path)
                }
                txtSubtitle?.text = "${displaySongs.size} Songs • By You"
            } else {
                txtSubtitle?.text = "0 Songs"
            }

            // --- ADAPTER UPDATE 1 ---
            rvPlaylistSongs?.adapter = SongAdapter(displaySongs,
                onSongClicked = { clickedSong ->
                    val index = displaySongs.indexOf(clickedSong)
                    PlayerManager.startPlaying(requireContext(), displaySongs, index)
                },
                onMoreOptionsClicked = { clickedSong ->
                    showSongOptionsBottomSheet(clickedSong)
                }
            )
        }

        btnPlayAll?.setOnClickListener {
            if (displaySongs.isNotEmpty()) {
                PlayerManager.startPlaying(requireContext(), displaySongs, 0)
            }
        }

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }

    // --- DATABASE FETCHER ---
    private fun fetchCustomPlaylistSongs(playlistId: Int, rvTracks: RecyclerView?, txtSubtitle: TextView?) {
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val savedSongEntities = db.playlistDao().getSongsInPlaylist(playlistId)
            val allLocalSongs = PlayerManager.allSongs

            // We grab the local offline songs, OR we rebuild the online Jamendo songs!
            displaySongs = savedSongEntities.mapNotNull { entity ->
                if (entity.isOnline) {
                    // For online songs, we just need the path (URL) to play it.
                    // (In a full app, you might save the title/artist to the Room DB too so it displays beautifully!)
                    Song("Online Track", "Jamendo Audio", "00:00", entity.songPath, isOnline = true)
                } else {
                    allLocalSongs.find { it.path == entity.songPath }
                }
            }

            withContext(Dispatchers.Main) {
                txtSubtitle?.text = "${displaySongs.size} Songs • By You"

                // --- ADAPTER UPDATE 2 ---
                rvTracks?.adapter = SongAdapter(displaySongs,
                    onSongClicked = { clickedSong ->
                        val clickedIndex = displaySongs.indexOf(clickedSong)
                        PlayerManager.startPlaying(requireContext(), displaySongs, clickedIndex)
                    },
                    onMoreOptionsClicked = { clickedSong ->
                        showSongOptionsBottomSheet(clickedSong)
                    }
                )
            }
        }
    }

    // --- JAMENDO INTERNET FETCHER ---
    private fun fetchAlbumTracks(albumId: String, rvTracks: RecyclerView?, albumArt: String, txtSubtitle: TextView?, albumArtist: String?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getApiService(requireContext()).getTracksByAlbum(
                    clientId = JAMENDO_CLIENT_ID,
                    albumId = albumId
                )

                withContext(Dispatchers.Main) {
                    val tracks = response.results.firstOrNull()?.tracks ?: emptyList()

                    displaySongs = tracks.map { track ->
                        Song(
                            title = track.title ?: "Unknown Track",
                            artist = track.artist ?: albumArtist ?: "Unknown Artist",
                            duration = formatDuration(track.duration ?: 0),
                            path = track.audioUrl ?: "",
                            isOnline = true,
                            imageUrl = track.imageUrl ?: albumArt
                        )
                    }

                    txtSubtitle?.text = "${displaySongs.size} Tracks"

                    // --- ADAPTER UPDATE 3 ---
                    rvTracks?.adapter = SongAdapter(displaySongs,
                        onSongClicked = { clickedSong ->
                            val clickedIndex = displaySongs.indexOf(clickedSong)
                            PlayerManager.startPlaying(requireContext(), displaySongs, clickedIndex)
                        },
                        onMoreOptionsClicked = { clickedSong ->
                            showSongOptionsBottomSheet(clickedSong)
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API_ERROR", "Failed to load album tracks", e)
                    Toast.makeText(requireContext(), "Failed to load tracks. Check internet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "00:00"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    // --- BOTTOM SHEET LOGIC ---
    private fun showSongOptionsBottomSheet(song: Song) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_song_options, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.bsSongTitle).text = song.title
        view.findViewById<TextView>(R.id.bsSongArtist).text = song.artist

        val isFav = PlayerManager.favoriteSongs.contains(song.path)
        val iconFav = view.findViewById<ImageView>(R.id.bsIconFavorite)
        val textFav = view.findViewById<TextView>(R.id.bsTextFavorite)
        if (isFav) {
            iconFav.setImageResource(android.R.drawable.star_on)
            textFav.text = "Remove from Favorites"
        }

        view.findViewById<View>(R.id.bsOptionAddToPlaylist).setOnClickListener {
            bottomSheetDialog.dismiss()
            showSelectPlaylistDialog(song)
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
                            val newEntry = PlaylistSongEntity(
                                playlistId = selectedPlaylist.id,
                                songPath = song.path,
                                isOnline = song.isOnline
                            )
                            db.playlistDao().insertSongIntoPlaylist(newEntry)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Added to ${selectedPlaylist.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }
}