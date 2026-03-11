package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Playlist

class QuickMixAdapter(
    private val playlists: List<Playlist>,
    private val onMixClicked: (Playlist) -> Unit
) : RecyclerView.Adapter<QuickMixAdapter.MixViewHolder>() {

    inner class MixViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMixName: TextView = itemView.findViewById(R.id.txtMixName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MixViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_mix, parent, false)
        return MixViewHolder(view)
    }

    override fun onBindViewHolder(holder: MixViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.txtMixName.text = playlist.title
        holder.itemView.setOnClickListener {
            onMixClicked(playlist)
        }
    }

    override fun getItemCount(): Int = playlists.size
}