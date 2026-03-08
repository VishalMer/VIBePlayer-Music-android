package com.vishal.vibeplayer.adapter

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song
import com.bumptech.glide.load.engine.DiskCacheStrategy

class SquareSongAdapter(
    private val songs: List<Song>,
    private val onTrackClick: (Song) -> Unit
) : RecyclerView.Adapter<SquareSongAdapter.SquareViewHolder>() {

    class SquareViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivTrackCover)
        val tvTitle: TextView = view.findViewById(R.id.tvTrackTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track_square, parent, false)
        return SquareViewHolder(view)
    }

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) {
        val song = songs[position]
        holder.tvTitle.text = song.title

        // --- THE ULTIMATE SMART IMAGE LOADER ---
        if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
            // SCENARIO 1: ONLINE MODE (Jamendo URLs)
            Glide.with(holder.itemView.context)
                .load(song.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivCover)
        } else {
            // SCENARIO 2 & 3: OFFLINE MODE
            if (song.art != null) {
                // If the bitmap is already loaded
                holder.ivCover.setImageBitmap(song.art)
            } else {
                // Extract the embedded cover art from the raw MP3 file path
                val artBytes = getAlbumArt(song.path)
                if (artBytes != null) {
                    Glide.with(holder.itemView.context)
                        .asBitmap()
                        .load(artBytes)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.ivCover)
                } else {
                    holder.ivCover.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }

        holder.itemView.setOnClickListener {
            onTrackClick(song)
        }
    }

    override fun getItemCount(): Int = songs.size

    // Helper function to pull ID3 cover art from local files
    private fun getAlbumArt(path: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }
}