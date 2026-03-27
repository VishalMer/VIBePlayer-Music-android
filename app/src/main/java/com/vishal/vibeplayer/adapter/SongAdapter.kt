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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClicked: (Song) -> Unit,
    private val onMoreOptionsClicked: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    companion object {
        private val artLoaderDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 8
        private val artCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }

        private val noArtCache = HashSet<String>()
    }

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

        // ==========================================
        // THE FIX: ALWAYS ATTACH CLICK LISTENERS FIRST
        // This guarantees that no matter what happens with the images below,
        // every single song and 3-dots menu will ALWAYS be clickable!
        // ==========================================
        holder.itemView.setOnClickListener { onSongClicked(song) }
        holder.btnMoreOptions.setOnClickListener { onMoreOptionsClicked(song) }

        holder.txtTitle.text = song.title
        holder.txtArtist.text = song.artist
        holder.txtDuration.text = song.duration

        holder.imgArt.setImageResource(R.drawable.bg_default_cover)
        val currentPath = song.path ?: ""
        holder.imgArt.tag = currentPath

        if (song.path == com.vishal.vibeplayer.manager.PlayerManager.currentSong?.path) {
            holder.txtTitle.setTextColor(android.graphics.Color.parseColor("#1DB954"))
        } else {
            holder.txtTitle.setTextColor(android.graphics.Color.WHITE)
        }

        if (song.isOnline && !song.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(song.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bg_default_cover)
                .into(holder.imgArt)
        } else if (!song.isOnline && currentPath.isNotEmpty()) {

            val cachedBitmap = artCache.get(currentPath)

            // ==========================================
            // THE FIX: REMOVED THE 'return' STATEMENTS!
            // We use standard if/else logic so it safely checks the cache
            // without prematurely terminating the entire function.
            // ==========================================
            if (cachedBitmap != null) {
                holder.imgArt.setImageBitmap(cachedBitmap)
            } else if (!noArtCache.contains(currentPath)) {

                CoroutineScope(artLoaderDispatcher).launch {
                    var retriever: MediaMetadataRetriever? = null
                    try {
                        retriever = MediaMetadataRetriever()
                        retriever.setDataSource(currentPath)
                        val artBytes = retriever.embeddedPicture

                        withContext(Dispatchers.Main) {
                            if (artBytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                                if (bitmap != null) {
                                    artCache.put(currentPath, bitmap)
                                    if (holder.imgArt.tag == currentPath) {
                                        holder.imgArt.setImageBitmap(bitmap)
                                    }
                                }
                            } else {
                                noArtCache.add(currentPath)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { noArtCache.add(currentPath) }
                    } finally {
                        try {
                            retriever?.release()
                        } catch (e: Exception) {
                            // Ignore release errors safely
                        }
                    }
                }
            }
        } else if (song.art != null) {
            holder.imgArt.setImageBitmap(song.art)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun removeSong(position: Int) {
        val updatedList = songs.toMutableList()
        updatedList.removeAt(position)
        songs = updatedList
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, songs.size)
    }

    fun moveSong(fromPosition: Int, toPosition: Int) {
        val mutableList = songs.toMutableList()
        val movedItem = mutableList.removeAt(fromPosition)
        mutableList.add(toPosition, movedItem)
        songs = mutableList
        notifyItemMoved(fromPosition, toPosition)
    }

    fun updateData(newSongs: List<Song>) {
        val oldSize = this.songs.size
        this.songs = newSongs
        if (newSongs.size > oldSize) {
            notifyItemRangeInserted(oldSize, newSongs.size - oldSize)
        } else {
            notifyDataSetChanged()
        }
    }
}