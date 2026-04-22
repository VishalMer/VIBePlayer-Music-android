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
    private val onItemClick: (Playlist) -> Unit,
    private val onOptionsClick: (Playlist, View) -> Unit
) : RecyclerView.Adapter<YourPlaylistAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtPlaylistTitle)
        val txtSubtitle: TextView = itemView.findViewById(R.id.txtPlaylistSubtitle)
        val imgCover: ImageView = itemView.findViewById(R.id.imgPlaylistCover)
        val btnOptions: ImageView = itemView.findViewById(R.id.btnPlaylistOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.txtTitle.text = playlist.title
        holder.txtSubtitle.text = playlist.subtitle

        Glide.with(holder.itemView.context).clear(holder.imgCover)
        holder.imgCover.setImageDrawable(null)

        holder.imgCover.tag = playlist.coverPath

        if (!playlist.coverPath.isNullOrEmpty()) {

            holder.imgCover.visibility = View.VISIBLE

            val firstSong = PlayerManager.allSongs.find { it.path == playlist.coverPath }

            if (firstSong != null && firstSong.isOnline && !firstSong.imageUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(firstSong.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.imgCover)
            } else if (firstSong != null && firstSong.art != null) {
                holder.imgCover.setImageBitmap(firstSong.art)
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    val retriever = MediaMetadataRetriever()
                    var artBytes: ByteArray? = null
                    try {
                        retriever.setDataSource(playlist.coverPath)
                        artBytes = retriever.embeddedPicture
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        if (holder.imgCover.tag == playlist.coverPath) {
                            if (artBytes != null) {
                                Glide.with(holder.itemView.context)
                                    .asBitmap()
                                    .load(artBytes)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(holder.imgCover)
                            } else {
                                holder.imgCover.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        } else {
            holder.imgCover.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(playlist)
        }

        holder.btnOptions.setOnClickListener {
            onOptionsClick(playlist, holder.btnOptions)
        }
    }

    override fun getItemCount(): Int = playlists.size
}