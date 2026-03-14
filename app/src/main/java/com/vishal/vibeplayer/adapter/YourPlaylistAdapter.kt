package com.vishal.vibeplayer.adapter

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.manager.PlayerManager
import com.vishal.vibeplayer.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YourPlaylistAdapter(
    private val playlists: List<Playlist>,
    private val onItemClick: (Playlist) -> Unit
) : RecyclerView.Adapter<YourPlaylistAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtPlaylistTitle)
        val txtSubtitle: TextView = itemView.findViewById(R.id.txtPlaylistSubtitle)
        val imgCover: ImageView = itemView.findViewById(R.id.imgPlaylistCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.txtTitle.text = playlist.title
        holder.txtSubtitle.text = playlist.subtitle

        // 1. Clear previous image to prevent flickering when scrolling
        holder.imgCover.setImageDrawable(null)

        // 2. Fetch the art!
        if (!playlist.coverPath.isNullOrEmpty()) {
            val firstSong = PlayerManager.allSongs.find { it.path == playlist.coverPath }

            if (firstSong != null && firstSong.isOnline && !firstSong.imageUrl.isNullOrEmpty()) {
                // Online Songs (Glide handles URLs perfectly)
                Glide.with(holder.itemView.context)
                    .load(firstSong.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.imgCover)
            } else if (firstSong != null && firstSong.art != null) {
                // Local Songs (If bitmap is already loaded in RAM)
                holder.imgCover.setImageBitmap(firstSong.art)
            } else {
                // 🔥 THE FOOLPROOF FIX: Extract image bytes directly from the MP3 file!
                // We do this in the background so it doesn't freeze the app
                CoroutineScope(Dispatchers.IO).launch {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(playlist.coverPath)
                        val artBytes = retriever.embeddedPicture // Yank out the raw image!

                        withContext(Dispatchers.Main) {
                            if (artBytes != null) {
                                // Hand the raw bytes to Glide
                                Glide.with(holder.itemView.context)
                                    .asBitmap()
                                    .load(artBytes)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(holder.imgCover)
                            }
                        }
                    } catch (e: Exception) {
                        // If the mp3 has no album art, it gracefully fails and leaves your gradient!
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(playlist)
        }
    }

    override fun getItemCount(): Int = playlists.size
}