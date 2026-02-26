package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song

// 1. We added the click listener to the constructor
class SongAdapter(
    private val songList: List<Song>,
    private val onItemClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = itemView.findViewById(R.id.txtSongArtist)
        val txtDuration: TextView = itemView.findViewById(R.id.txtSongDuration)
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

        // 2. Tell the whole row to listen for a tap, and pass the specific song back!
        holder.itemView.setOnClickListener {
            onItemClick(currentSong)
        }
    }

    override fun getItemCount(): Int {
        return songList.size
    }
}