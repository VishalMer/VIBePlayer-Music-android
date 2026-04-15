package com.vishal.vibeplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.model.FeaturedPlaylist

class FeaturedPlaylistAdapter(
    private val playlists: List<FeaturedPlaylist>,
    private val onItemClick: (FeaturedPlaylist) -> Unit // Now passes the FeaturedPlaylist
) : RecyclerView.Adapter<FeaturedPlaylistAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtTitle: TextView = itemView.findViewById(R.id.txtFeaturedTitle)

        // NEW: Grab the icon and background views
        val imgIcon: ImageView = itemView.findViewById(R.id.imgFeaturedIcon)
        val layoutBackground: View = itemView.findViewById(R.id.layoutFeaturedBackground)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_featured_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val featuredPlaylist = playlists[position]

        // Set the dynamic data
        holder.txtTitle.text = featuredPlaylist.title
        holder.imgIcon.setImageResource(featuredPlaylist.iconRes)
        holder.layoutBackground.setBackgroundResource(featuredPlaylist.backgroundRes)

        // Listen for clicks and pass the whole object back
        holder.itemView.setOnClickListener {
            onItemClick(featuredPlaylist)
        }
    }

    override fun getItemCount(): Int = playlists.size
}