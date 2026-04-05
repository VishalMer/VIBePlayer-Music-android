package com.vishal.vibeplayer.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
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
import com.vishal.vibeplayer.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsFragment : Fragment() {

    private lateinit var db: AppDatabase
    private var dbPlaylists: List<PlaylistEntity> = emptyList()
    private lateinit var rvYour: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_playlists, container, false)

        // 1. Initialize the Room Database
        db = AppDatabase.getDatabase(requireContext())

        // --- FEATURED PLAYLISTS (Static) ---
        val rvFeatured = view.findViewById<RecyclerView>(R.id.rvFeaturedPlaylists)
        // --- FEATURED PLAYLISTS (Static) ---
        val featuredData = listOf(
            // We let the 'id' safely default to -1 in the background
            // and pass the text to the 'title' and 'subtitle' exactly as the data class wants!
            Playlist(title = "Liked Songs", subtitle = "Your favorites"),
            Playlist(title = "Party Hits", subtitle = "Top trending"),
            Playlist(title = "Chill Mix", subtitle = "Relaxing vibes"),
            Playlist(title = "Workout", subtitle = "Pump it up")
        )
        rvFeatured.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvFeatured.adapter = FeaturedPlaylistAdapter(featuredData) { clickedPlaylist ->
            val bundle = Bundle()
            bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)
            findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
        }

        // --- YOUR PLAYLISTS (Dynamic Database) ---
        rvYour = view.findViewById<RecyclerView>(R.id.rvYourPlaylists)
        rvYour.layoutManager = LinearLayoutManager(requireContext())

        // Load the saved playlists from memory!
        loadCustomPlaylists()

        // --- CREATE BUTTON ---
        // IMPORTANT: Make sure your '+ Create' button in XML has this ID!
        val btnCreate = view.findViewById<View>(R.id.btnCreatePlaylist)
        btnCreate?.setOnClickListener {
            showCreatePlaylistDialog()
        }

        return view
    }

    // --- POPUP DIALOG LOGIC ---
    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("New Playlist")

        // Build a sleek input box
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

    // --- DATABASE OPERATIONS ---
    private fun savePlaylistToDatabase(name: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val newPlaylist = PlaylistEntity(name = name)
            db.playlistDao().insertPlaylist(newPlaylist)

            // Instantly refresh the UI to show the new playlist
            loadCustomPlaylists()
        }
    }

    private fun loadCustomPlaylists() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // 1. THE FIX: Force favorites to load into memory FIRST!
            PlayerManager.loadFavorites(requireContext())

            dbPlaylists = db.playlistDao().getAllPlaylists()

            val dynamicYourData = dbPlaylists.map { entity ->
                // Grab the FIRST song to match the top of the list
                val firstAddedSong = db.playlistDao().getSongsInPlaylist(entity.id).firstOrNull()

                Playlist(
                    id = entity.id,
                    title = entity.name,
                    subtitle = "Custom Playlist • By You",
                    coverPath = firstAddedSong?.songPath
                )
            }

            // 2. THE FIX: Now this will successfully find the first favorited song!
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
                rvYour.adapter = YourPlaylistAdapter(finalData) { clickedPlaylist ->
                    val bundle = Bundle()
                    bundle.putString("PLAYLIST_NAME", clickedPlaylist.title)

                    val matchedEntity = dbPlaylists.find { it.name == clickedPlaylist.title }
                    if (matchedEntity != null) {
                        bundle.putInt("CUSTOM_PLAYLIST_ID", matchedEntity.id)
                    }

                    findNavController().navigate(R.id.action_playlistsFragment_to_playlistDetailsFragment, bundle)
                }
            }
        }
    }
}