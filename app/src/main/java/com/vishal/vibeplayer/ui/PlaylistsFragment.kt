package com.vishal.vibeplayer.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.FeaturedPlaylistAdapter
import com.vishal.vibeplayer.adapter.YourPlaylistAdapter
import com.vishal.vibeplayer.database.AppDatabase
import com.vishal.vibeplayer.database.PlaylistEntity
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.FeaturedPlaylist
import com.vishal.vibeplayer.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView

class PlaylistsFragment : Fragment() {

    private lateinit var db: AppDatabase
    private var dbPlaylists: List<PlaylistEntity> = emptyList()
    private lateinit var rvYour: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)

        db = AppDatabase.getDatabase(requireContext())

        val rvFeatured = view.findViewById<RecyclerView>(R.id.rvFeaturedPlaylists)

        val featuredData = listOf(
            FeaturedPlaylist(-1, "Liked Songs", R.drawable.ic_heart_fill, R.drawable.bg_gradient_br_to_tl),
            FeaturedPlaylist(-2, "Most Played", R.drawable.ic_music_library, R.drawable.bg_gradient_tl_to_br),
            FeaturedPlaylist(-3, "Recently Added", R.drawable.ic_recent, R.drawable.bg_gradient_bl_to_tr),
            FeaturedPlaylist(-4, "Downloads", R.drawable.ic_download, R.drawable.bg_gradient_tr_to_bl)
        )

        rvFeatured.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvFeatured.adapter = FeaturedPlaylistAdapter(featuredData) { clickedPlaylist ->
            val bundle = Bundle()
            bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)
            bundle.putInt("CUSTOM_PLAYLIST_ID", clickedPlaylist.specialId)
            findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
        }

        rvYour = view.findViewById<RecyclerView>(R.id.rvYourPlaylists)
        rvYour.layoutManager = LinearLayoutManager(requireContext())

        loadCustomPlaylists()

        val btnCreate = view.findViewById<View>(R.id.btnCreatePlaylist)
        btnCreate?.setOnClickListener {
            showCreatePlaylistDialog()
        }

        return view
    }

    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("New Playlist")

        val input = EditText(requireContext())
        input.hint = "Name your mixtape..."

        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(50, 20, 50, 0)
        input.layoutParams = params
        container.addView(input)

        builder.setView(container)

        builder.setPositiveButton("Create") { dialog, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                savePlaylistToDatabase(name)
            } else {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun savePlaylistToDatabase(name: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val newPlaylist = PlaylistEntity(name = name)
            db.playlistDao().insertPlaylist(newPlaylist)
            loadCustomPlaylists()
        }
    }

    private fun loadCustomPlaylists() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PlayerManager.loadFavorites(requireContext())

            dbPlaylists = db.playlistDao().getAllPlaylists()

            val dynamicYourData = dbPlaylists.map { entity ->
                val firstAddedSong = db.playlistDao().getSongsInPlaylist(entity.id).firstOrNull()
                Playlist(
                    id = entity.id,
                    title = entity.name,
                    subtitle = "Custom Playlist • By You",
                    coverPath = firstAddedSong?.songPath
                )
            }

            val firstFavPath = PlayerManager.favoriteSongs.firstOrNull()
            val finalData = mutableListOf(
                Playlist(
                    title = "My Favorites",
                    subtitle = "Saved • By You",
                    coverPath = firstFavPath
                )
            )
            finalData.addAll(dynamicYourData)

            withContext(Dispatchers.Main) {
                rvYour.adapter = YourPlaylistAdapter(
                    finalData,
                    onItemClick = { clickedPlaylist ->
                        val bundle = Bundle()
                        bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)

                        val matchedEntity = dbPlaylists.find { it.name == clickedPlaylist.title }
                        if (matchedEntity != null) {
                            bundle.putInt("CUSTOM_PLAYLIST_ID", matchedEntity.id)
                        }

                        findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
                    },
                    onOptionsClick = { playlist, _ ->
                        if (playlist.title != "My Favorites") {
                            showPlaylistBottomSheet(playlist)
                        }
                    }
                )
            }
        }
    }

    private fun showPlaylistBottomSheet(playlist: Playlist) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_playlist_options, null)
        bottomSheetDialog.setContentView(view)

        val txtTitle = view.findViewById<TextView>(R.id.txtSheetTitle)
        txtTitle.text = playlist.title

        val txtSubtitle = view.findViewById<TextView>(R.id.txtSheetSubtitle)
        txtSubtitle.text = playlist.subtitle

        view.findViewById<View>(R.id.btnPlayNow).setOnClickListener {
            bottomSheetDialog.dismiss()
            playPlaylist(playlist.id, shuffle = false)
        }

        view.findViewById<View>(R.id.btnShuffle).setOnClickListener {
            bottomSheetDialog.dismiss()
            playPlaylist(playlist.id, shuffle = true)
        }

        view.findViewById<View>(R.id.btnRename).setOnClickListener {
            bottomSheetDialog.dismiss()
            showRenameDialog(playlist)
        }

        view.findViewById<View>(R.id.btnDelete).setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeleteConfirmationDialog(playlist)
        }

        bottomSheetDialog.show()
    }

    private fun playPlaylist(playlistId: Int, shuffle: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dbSongs = db.playlistDao().getSongsInPlaylist(playlistId)

            var realSongs = dbSongs.mapNotNull { entity ->
                PlayerManager.allSongs.find { it.path == entity.songPath }
            }

            if (realSongs.isNotEmpty()) {
                if (shuffle) {
                    realSongs = realSongs.shuffled()
                }

                withContext(Dispatchers.Main) {
                    PlayerManager.startPlaying(requireContext(), realSongs, 0)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Playlist is empty!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showRenameDialog(playlist: Playlist) {
        val editText = EditText(requireContext())
        editText.setText(playlist.title)
        editText.setSelection(playlist.title.length)

        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(50, 20, 50, 0)
        editText.layoutParams = params
        container.addView(editText)

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Playlist")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != playlist.title) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val entity = dbPlaylists.find { it.id == playlist.id }
                        if (entity != null) {
                            val updatedEntity = entity.copy(name = newName)
                            db.playlistDao().updatePlaylist(updatedEntity)
                            loadCustomPlaylists()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete '${playlist.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val entity = dbPlaylists.find { it.id == playlist.id }
                    if (entity != null) {
                        db.playlistDao().deletePlaylist(entity)
                        loadCustomPlaylists()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}