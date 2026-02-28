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

class SquareSongAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SquareSongAdapter.SquareViewHolder>() {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val albumArtCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    inner class SquareViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // 🚨 CRUCIAL: Check your item_song_card_square.xml and make sure these IDs match!
        val imgArt: ImageView = itemView.findViewById(R.id.imgSquareArt) // Change this if your image ID is different
        val txtTitle: TextView? = itemView.findViewById(R.id.txtSquareTitle) // Change this if your text ID is different (or remove if you only show images)

        var imageLoadJob: Job? = null

        fun bind(song: Song) {
            txtTitle?.text = song.title
            txtTitle?.isSelected = true
            imgArt.setImageResource(android.R.drawable.ic_menu_gallery)

            val cachedBitmap = albumArtCache.get(song.path)
            if (cachedBitmap != null) {
                imgArt.setImageBitmap(cachedBitmap)
                return
            }

            imageLoadJob?.cancel()
            imageLoadJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(song.path)
                    val artBytes = retriever.embeddedPicture
                    retriever.release()

                    if (artBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        albumArtCache.put(song.path, bitmap)
                        withContext(Dispatchers.Main) {
                            imgArt.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            itemView.setOnClickListener { onItemClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SquareViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_card_square, parent, false)
        return SquareViewHolder(view)
    }

    override fun onBindViewHolder(holder: SquareViewHolder, position: Int) = holder.bind(songs[position])

    override fun getItemCount(): Int = songs.size

    override fun onViewRecycled(holder: SquareViewHolder) {
        super.onViewRecycled(holder)
        holder.imageLoadJob?.cancel()
    }
}