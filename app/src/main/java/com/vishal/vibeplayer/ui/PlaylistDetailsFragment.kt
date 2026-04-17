package com.vishal.vibeplayer.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
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
        // Get the artist from arguments, default to empty string if missing
        val albumArtist = arguments?.getString("ALBUM_ARTIST") ?: ""

        var customPlaylistId = arguments?.getInt("CUSTOM_PLAYLIST_ID", 0) ?: 0
        if (playlistName.equals("My Favorites", ignoreCase = true) ||
            playlistName.equals("Liked Songs", ignoreCase = true)) {
            customPlaylistId = -1
        }

        if (albumId != null) {
            // SCENARIO 1: ONLINE JAMENDO ALBUM
            txtTitle?.text = albumName ?: "Unknown Album"
            txtSubtitle?.text = if (albumArtist.isNotEmpty()) albumArtist else "Unknown Artist"

            if (ivCover != null && albumArt != null) {
                Glide.with(this)
                    .load(albumArt)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivCover)
            }

            // FIX: Pass the String 'albumArtist' instead of the TextView 'txtSubtitle' in the 4th parameter position
            fetchAlbumTracks(albumId, rvPlaylistSongs, albumArt ?: "", albumArtist, txtSubtitle)

        } else {
            // SCENARIO 2: LOCAL OR CUSTOM PLAYLISTS
            txtTitle?.text = playlistName ?: "Playlist"

            when (customPlaylistId) {
                -1 -> {
                    PlayerManager.loadFavorites(requireContext())
                    displaySongs = PlayerManager.allSongs.filter { PlayerManager.favoriteSongs.contains(it.path) }
                    txtSubtitle?.text = "${displaySongs.size} Songs • By You"
                    setupLocalPlaylistRecyclerView(rvPlaylistSongs, -1, txtSubtitle)
                    updateBigCoverArt(ivCover)
                }
                -2 -> {
                    txtSubtitle?.text = "Sorting your favorites..."
                    viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(300)
                        val sortedMostPlayed = withContext(Dispatchers.Default) {
                            PlayerManager.allSongs.sortedByDescending { song ->
                                PlayerManager.playCounts[song.path ?: ""] ?: 0
                            }
                        }
                        displaySongs = sortedMostPlayed
                        txtSubtitle?.text = "${displaySongs.size} Tracks"
                        setupLocalPlaylistRecyclerView(rvPlaylistSongs, -2, txtSubtitle)
                        updateBigCoverArt(ivCover)
                    }
                }
                -3 -> {
                    displaySongs = PlayerManager.playHistory.toList()
                    txtSubtitle?.text = "${displaySongs.size} Recently Played"
                    setupLocalPlaylistRecyclerView(rvPlaylistSongs, -3, txtSubtitle)
                    updateBigCoverArt(ivCover)
                }
                -4 -> {
                    displaySongs = emptyList()
                    txtSubtitle?.text = "0 Downloads"
                    setupLocalPlaylistRecyclerView(rvPlaylistSongs, -4, txtSubtitle)
                }
                else -> {
                    fetchCustomPlaylistSongs(customPlaylistId, rvPlaylistSongs, txtSubtitle, ivCover)
                }
            }
        }

        btnPlayAll?.setOnClickListener {
            if (displaySongs.isNotEmpty()) {
                PlayerManager.startPlaying(requireContext(), displaySongs.toMutableList(), 0)
            }
        }

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }

    private fun setupLocalPlaylistRecyclerView(rvTracks: RecyclerView?, playlistId: Int, txtSubtitle: TextView?) {
        rvTracks?.adapter = SongAdapter(displaySongs,
            onSongClicked = { clickedSong ->
                val index = displaySongs.indexOfFirst { it.title == clickedSong.title && it.artist == clickedSong.artist }
                if (index != -1) {
                    PlayerManager.startPlaying(requireContext(), displaySongs.toMutableList(), index)
                } else {
                    Toast.makeText(requireContext(), "Error: Song not found", Toast.LENGTH_SHORT).show()
                }
            },
            onMoreOptionsClicked = { clickedSong ->
                showSongOptionsBottomSheet(clickedSong)
            }
        )

        if (playlistId == -1 || playlistId == -3) {
            val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean { return false }
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val songToRemove = displaySongs[position]

                    (rvTracks?.adapter as? SongAdapter)?.removeSong(position)
                    val mutableList = displaySongs.toMutableList()
                    mutableList.removeAt(position)
                    displaySongs = mutableList

                    val suffix = if (playlistId == -1) "Songs • By You" else "Recently Played"
                    txtSubtitle?.text = "${displaySongs.size} $suffix"

                    if (playlistId == -1) {
                        val temp = PlayerManager.currentSong
                        PlayerManager.currentSong = songToRemove
                        PlayerManager.toggleFavorite(requireContext())
                        PlayerManager.currentSong = temp
                        Toast.makeText(requireContext(), "Removed from Favorites", Toast.LENGTH_SHORT).show()
                    } else if (playlistId == -3) {
                        PlayerManager.playHistory.removeAll { it.path == songToRemove.path }
                        Toast.makeText(requireContext(), "Removed from History", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ItemTouchHelper(swipeHandler).attachToRecyclerView(rvTracks)
        }
    }

    private fun fetchCustomPlaylistSongs(playlistId: Int, rvTracks: RecyclerView?, txtSubtitle: TextView?, ivCover: ImageView?) {
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val savedSongEntities = db.playlistDao().getSongsInPlaylist(playlistId)
            val allLocalSongs = PlayerManager.allSongs

            displaySongs = savedSongEntities.mapNotNull { entity ->
                if (entity.isOnline) {
                    Song("Online Track", "Jamendo Audio", "00:00", entity.songPath, isOnline = true)
                } else {
                    allLocalSongs.find { it.path == entity.songPath }
                }
            }

            withContext(Dispatchers.Main) {
                txtSubtitle?.text = "${displaySongs.size} Songs • By You"
                updateBigCoverArt(ivCover)

                val adapter = SongAdapter(displaySongs,
                    onSongClicked = { clickedSong ->
                        val clickedIndex = displaySongs.indexOfFirst { it.title == clickedSong.title && it.artist == clickedSong.artist }
                        if (clickedIndex != -1) {
                            PlayerManager.startPlaying(requireContext(), displaySongs.toMutableList(), clickedIndex)
                        } else {
                            Toast.makeText(requireContext(), "Error: Song not found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoreOptionsClicked = { clickedSong ->
                        showSongOptionsBottomSheet(clickedSong)
                    }
                )
                rvTracks?.adapter = adapter

                val swipeHandler = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                        val fromPosition = viewHolder.adapterPosition
                        val toPosition = target.adapterPosition
                        (rvTracks?.adapter as? SongAdapter)?.moveSong(fromPosition, toPosition)
                        val mutableList = displaySongs.toMutableList()
                        val movedSong = mutableList.removeAt(fromPosition)
                        mutableList.add(toPosition, movedSong)
                        displaySongs = mutableList
                        return true
                    }
                    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                        super.clearView(recyclerView, viewHolder)
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            db.playlistDao().removeAllSongsFromPlaylist(playlistId)
                            displaySongs.forEach { song ->
                                val newEntry = PlaylistSongEntity(playlistId = playlistId, songPath = song.path ?: "", isOnline = song.isOnline)
                                db.playlistDao().insertSongIntoPlaylist(newEntry)
                            }
                        }
                    }
                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val position = viewHolder.adapterPosition
                        val songToRemove = displaySongs[position]
                        (rvTracks?.adapter as? SongAdapter)?.removeSong(position)
                        val mutableList = displaySongs.toMutableList()
                        mutableList.removeAt(position)
                        displaySongs = mutableList
                        txtSubtitle?.text = "${displaySongs.size} Songs • By You"
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            db.playlistDao().removeSongFromPlaylist(playlistId, songToRemove.path ?: "")
                            withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Removed from playlist", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
                ItemTouchHelper(swipeHandler).attachToRecyclerView(rvTracks)
            }
        }
    }

    private fun fetchAlbumTracks(
        albumId: String,
        rvTracks: RecyclerView,
        albumArt: String,
        albumArtist: String,
        txtSubtitle: TextView?
    ) {
        txtSubtitle?.text = "Loading tracks..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.getApiService(requireContext())
                    .getTracksByAlbum(clientId = JAMENDO_CLIENT_ID, albumId = albumId)

                val tracks = response.results

                withContext(Dispatchers.Main) {
                    if (tracks.isEmpty()) {
                        txtSubtitle?.text = "No tracks found."
                        return@withContext
                    }

                    displaySongs = tracks.map { track ->
                        Song(
                            title = track.title ?: "Unknown Track",
                            artist = track.artist ?: albumArtist,
                            duration = formatDuration(seconds = track.duration ?: 0),
                            path = track.audioUrl ?: "",
                            isOnline = true,
                            imageUrl = track.imageUrl ?: albumArt
                        )
                    }

                    txtSubtitle?.text = "${displaySongs.size} Tracks"

                    // FIX: Replaced TrackAdapter with SongAdapter
                    val songAdapter = SongAdapter(displaySongs,
                        onSongClicked = { clickedSong ->
                            val index = displaySongs.indexOf(clickedSong)
                            if(index != -1) {
                                PlayerManager.startPlaying(requireContext(), displaySongs, index)
                            }
                        },
                        onMoreOptionsClicked = { clickedSong ->
                            showSongOptionsBottomSheet(clickedSong)
                        }
                    )
                    rvTracks.adapter = songAdapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtSubtitle?.text = "Failed to load tracks."
                    e.printStackTrace()
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

    private fun showSongOptionsBottomSheet(song: Song) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
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
                            val existingSongs = db.playlistDao().getSongsInPlaylist(selectedPlaylist.id)
                            val isAlreadyAdded = existingSongs.any { it.songPath == song.path }

                            if (isAlreadyAdded) {
                                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Already added to ${selectedPlaylist.name}!", Toast.LENGTH_SHORT).show() }
                            } else {
                                val newEntry = PlaylistSongEntity(playlistId = selectedPlaylist.id, songPath = song.path ?: "", isOnline = song.isOnline)
                                db.playlistDao().insertSongIntoPlaylist(newEntry)
                                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Added to ${selectedPlaylist.name}", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun updateBigCoverArt(ivCover: ImageView?) {
        if (ivCover == null || displaySongs.isEmpty()) return

        val firstSong = displaySongs.first()

        if (firstSong.isOnline && !firstSong.imageUrl.isNullOrEmpty()) {
            Glide.with(this).load(firstSong.imageUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(ivCover)
        } else if (firstSong.art != null) {
            ivCover.setImageBitmap(firstSong.art)
        } else if (!firstSong.path.isNullOrEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                var artBytes: ByteArray? = null
                try {
                    retriever.setDataSource(firstSong.path)
                    artBytes = retriever.embeddedPicture
                } catch (e: Exception) {} finally {
                    try { retriever.release() } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    if (artBytes != null) {
                        Glide.with(this@PlaylistDetailsFragment).asBitmap().load(artBytes).diskCacheStrategy(DiskCacheStrategy.ALL).into(ivCover)
                    }
                }
            }
        }
    }
}