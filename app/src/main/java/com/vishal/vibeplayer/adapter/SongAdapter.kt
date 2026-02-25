package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Song

class SongAdapter(private val songList: List<Song>) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = itemView.findViewById(R.id.txtSongArtist)

        // 1. We added your new duration TextView here!
        // IMPORTANT: Make sure R.id.txtSongDuration perfectly matches the ID in your item_song_row.xml
        val txtDuration: TextView = itemView.findViewById(R.id.txtSongDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song_row, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val currentSong = songList[position]

        // 2. Now we feed the data into 3 separate text boxes instead of trying to merge them!
        holder.txtTitle.text = currentSong.title
        holder.txtArtist.text = currentSong.artist
        holder.txtDuration.text = currentSong.duration
    }

    override fun getItemCount(): Int {
        return songList.size
    }
}