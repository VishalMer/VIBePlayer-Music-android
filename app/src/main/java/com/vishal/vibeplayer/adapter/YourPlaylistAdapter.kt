package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Playlist

class YourPlaylistAdapter(
    private val playlists: List<Playlist>,
    private val onItemClick: (Playlist) -> Unit // Now passes the specific Playlist clicked
) : RecyclerView.Adapter<YourPlaylistAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtPlaylistTitle)
        val txtSubtitle: TextView = itemView.findViewById(R.id.txtPlaylistSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.txtTitle.text = playlists[position].title
        holder.txtSubtitle.text = playlists[position].subtitle

        holder.itemView.setOnClickListener {
            onItemClick(playlists[position]) // Send the playlist up
        }
    }

    override fun getItemCount(): Int = playlists.size
}