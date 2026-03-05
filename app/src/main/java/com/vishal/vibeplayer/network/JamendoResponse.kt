package com.vishal.vibeplayer.network

import com.google.gson.annotations.SerializedName

data class JamendoResponse(
    val results: List<JamendoTrack>
)

data class JamendoAlbumResponse(
    val results: List<JamendoAlbum>
)

data class JamendoAlbum(
    val id: String,
    val name: String,
    val artist_name: String,
    val image: String
)

// --- NEW CLASSES FOR THE ALBUMS/TRACKS ENDPOINT ---
data class JamendoAlbumTracksResponse(
    val results: List<JamendoAlbumWithTracks>
)

data class JamendoAlbumWithTracks(
    val id: String,
    val tracks: List<JamendoTrack>
)

data class JamendoTrack(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val title: String?,
    @SerializedName("artist_name") val artist: String?,
    @SerializedName("image") val imageUrl: String?,
    @SerializedName("audio") val audioUrl: String?,
    @SerializedName("duration") val duration: Int? // THE FIX: Safely expects an Integer now!
)