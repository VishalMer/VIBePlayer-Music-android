package com.vishal.vibeplayer.adapter

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song

class SongAdapter(
    private val songList: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = itemView.findViewById(R.id.txtSongArtist)
        val txtDuration: TextView = itemView.findViewById(R.id.txtSongDuration)

        // 1. Find the Image Box! (Make sure this matches your XML ID)
        val imgSongArt: ImageView = itemView.findViewById(R.id.imgSongArt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_row, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val currentSong = songList[position]
        holder.txtTitle.text = currentSong.title
        holder.txtArtist.text = currentSong.artist
        holder.txtDuration.text = currentSong.duration

        // 2. Reset the image to a placeholder.
        // (Because RecyclerView recycles UI rows when you scroll, we must clear the old image!)
        holder.imgSongArt.setImageResource(android.R.drawable.ic_menu_gallery)

        // 3. Tag the ImageView so we know exactly which song it belongs to
        holder.imgSongArt.tag = currentSong.path

        // 4. Extract the image in a Background Thread so scrolling stays smooth!
        Thread {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(currentSong.path)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)

                    // 5. Jump back to the Main UI Thread to set the image
                    holder.itemView.post {
                        // Check the tag to make sure the user hasn't scrolled past this row already!
                        if (holder.imgSongArt.tag == currentSong.path) {
                            holder.imgSongArt.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }
        }.start()

        holder.itemView.setOnClickListener {
            onItemClick(currentSong)
        }
    }

    override fun getItemCount(): Int {
        return songList.size
    }
}