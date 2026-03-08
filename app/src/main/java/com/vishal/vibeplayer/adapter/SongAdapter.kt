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
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongAdapter(
    private val songs: List<Song>,
    private val onSongClicked: (Song) -> Unit,
    private val onMoreOptionsClicked: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = view.findViewById(R.id.txtSongArtist)
        val txtDuration: TextView = view.findViewById(R.id.txtSongDuration)
        val imgArt: ImageView = view.findViewById(R.id.imgSongArt)
        val btnMoreOptions: ImageView = view.findViewById(R.id.btnMenuRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_row, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]

        holder.txtTitle.text = song.title
        holder.txtArtist.text = song.artist
        holder.txtDuration.text = song.duration

        // 1. Set default placeholder instantly so old images don't flash while fast-scrolling
        holder.imgArt.setImageResource(android.R.drawable.ic_menu_gallery)

        // 2. Tag the ImageView with the current path to prevent mismatched covers when recycling views!
        val currentPath = song.path ?: ""
        holder.imgArt.tag = currentPath

        // 3. Smart Image Loading Engine
        if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {

            // --- SCENARIO A: ONLINE JAMENDO TRACK ---
            Glide.with(holder.itemView.context)
                .load(song.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.imgArt)

        } else if (!song.isOnline && currentPath.isNotEmpty()) {

            // --- SCENARIO B: OFFLINE LOCAL MP3 ---
            // Launch a background thread so extracting art doesn't freeze the scrolling!
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(currentPath)
                    val artBytes = retriever.embeddedPicture // Extract the raw image bytes
                    retriever.release()

                    if (artBytes != null) {
                        withContext(Dispatchers.Main) {
                            // Verify the user hasn't quickly scrolled past this row before loading
                            if (holder.imgArt.tag == currentPath) {
                                // Hand the raw bytes to Glide so it can compress and cache them perfectly
                                Glide.with(holder.itemView.context)
                                    .asBitmap()
                                    .load(artBytes)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .into(holder.imgArt)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fails silently if the user's downloaded MP3 has no cover art attached
                }
            }

        } else if (song.art != null) {

            // --- SCENARIO C: ALREADY LOADED BITMAP (Fallback) ---
            holder.imgArt.setImageBitmap(song.art)

        }

        // --- CLICK LISTENERS ---
        holder.itemView.setOnClickListener { onSongClicked(song) }
        holder.btnMoreOptions.setOnClickListener { onMoreOptionsClicked(song) }
    }

    override fun getItemCount(): Int = songs.size
}