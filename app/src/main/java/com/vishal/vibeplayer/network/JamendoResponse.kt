package com.vishal.vibeplayer.network

import com.google.gson.annotations.SerializedName
import com.vishal.vibeplayer.model.Song

// Jamendo wraps all their songs inside a "results" array. This catches it!
data class JamendoResponse(
    @SerializedName("results") val results: List<Song>
)