package com.vishal.vibeplayer.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    // --- THE BRAIN'S MEMORY BANK FOR IMAGES ---
    // Uses 1/8th of the app's available memory to safely cache album art
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val albumArtCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // TODO: Ensure these IDs match your 'item_song_row.xml'
        val txtTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = itemView.findViewById(R.id.txtSongArtist)
        val txtDuration: TextView = itemView.findViewById(R.id.txtSongDuration)
        val imgArt: ImageView = itemView.findViewById(R.id.imgSongArt)

        // Keeps track of the background task so we can cancel it if the user scrolls super fast!
        var imageLoadJob: Job? = null

        fun bind(song: Song) {
            txtTitle.text = song.title
            txtArtist.text = song.artist
            txtDuration.text = song.duration

            // Set a default placeholder immediately so old images don't show up while scrolling
            imgArt.setImageResource(android.R.drawable.ic_menu_gallery)

            // 1. Check if we already have the image in our blazing-fast RAM cache
            val cachedBitmap = albumArtCache.get(song.path)
            if (cachedBitmap != null) {
                imgArt.setImageBitmap(cachedBitmap)
                return
            }

            // 2. If not in cache, cancel any old jobs on this row and load it in the background
            imageLoadJob?.cancel()
            imageLoadJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(song.path)
                    val artBytes = retriever.embeddedPicture
                    retriever.release()

                    if (artBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)

                        // Save it to the cache for next time
                        albumArtCache.put(song.path, bitmap)

                        // Push the image back to the main UI thread
                        withContext(Dispatchers.Main) {
                            imgArt.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // Catch broken files silently
                }
            }

            // Handle the click event
            itemView.setOnClickListener {
                onItemClick(song)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_row, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    // Clean up background jobs when the view goes off-screen to save battery
    override fun onViewRecycled(holder: SongViewHolder) {
        super.onViewRecycled(holder)
        holder.imageLoadJob?.cancel()
    }
}