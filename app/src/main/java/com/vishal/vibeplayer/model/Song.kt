package com.vishal.vibeplayer.model

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName

data class Song(
    // We use @SerializedName to tell Gson exactly which JSON text matches which variable!
    @SerializedName("title") val title: String = "Unknown Title",
    @SerializedName("artist") val artist: String = "Unknown Artist",
    @SerializedName("duration") val duration: String = "00:00",
    @SerializedName("path") val path: String = "",

    // @Transient tells the internet parser to completely ignore this local image variable
    @Transient var art: Bitmap? = null,

    // --- ONLINE FIELDS ---
    // --- ONLINE FIELDS (Mapped for Jamendo API) ---
    @SerializedName("id") val id: String = "",
    @SerializedName("audio") val audioUrl: String = "",    // Jamendo calls the URL 'audio'
    @SerializedName("image") val imageUrl: String = "",    // Jamendo calls the cover 'image'

    // NOTE: Change the @SerializedName for your title and artist above as well!
    // @SerializedName("name") val title: String = "Unknown Title",
    // @SerializedName("artist_name") val artist: String = "Unknown Artist",

    var isOnline: Boolean = false
)