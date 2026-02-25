package com.vishal.vibeplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.vibeplayer.R
import com.vishal.vibeplayer.adapter.SongAdapter
import com.vishal.vibeplayer.model.Song

class AllTracksFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_all_tracks, container, false)

        // 1. Find the RecyclerView
        val rvAllTracks = view.findViewById<RecyclerView>(R.id.rvAllTracks)

        // 2. Create some dummy data (Like fetching a JSON API)
        val mySongs = listOf(
            Song("Line Without a Hook", "Ricky Montgomery", "4:09"),
            Song("Raataan Lambiyan", "Tanishk Bagchi", "3:50"),
            Song("Blinding Lights", "The Weeknd", "3:20"),
            Song("Perfect", "Ed Sheeran", "4:23"),
            Song("Levitating", "Dua Lipa", "3:23"),
            Song("Starboy", "The Weeknd", "3:50"),
            Song("Shape of You", "Ed Sheeran", "3:53"),
            Song("Watermelon Sugar", "Harry Styles", "2:54"),
            Song("Bad Guy", "Billie Eilish", "3:14"),
            Song("Peaches", "Justin Bieber", "3:18")
        )

        // 3. Setup the RecyclerView with the Adapter
        rvAllTracks.layoutManager = LinearLayoutManager(requireContext())
        rvAllTracks.adapter = SongAdapter(mySongs)

        return view
    }
}