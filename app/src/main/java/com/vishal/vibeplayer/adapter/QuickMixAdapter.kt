package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.Playlist

class QuickMixAdapter(
    private val playlists: List<Playlist>,
    private val onMixClicked: (Playlist) -> Unit
) : RecyclerView.Adapter<QuickMixAdapter.MixViewHolder>() {

    inner class MixViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnMixPill: MaterialButton = itemView.findViewById(R.id.btnMixPill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MixViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_mix, parent, false)
        return MixViewHolder(view)
    }

    override fun onBindViewHolder(holder: MixViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.btnMixPill.text = playlist.title

        // Dynamically set the icon based on the playlist type!
        if (playlist.id == -1) {
            holder.btnMixPill.setIconResource(R.drawable.ic_heart_fill)
        } else {
            // Use your default playlist icon here. I'm using a placeholder name.
            holder.btnMixPill.setIconResource(R.drawable.ic_playlist)
        }

        holder.btnMixPill.setOnClickListener {
            onMixClicked(playlist)
        }
    }

    override fun getItemCount(): Int = playlists.size
}