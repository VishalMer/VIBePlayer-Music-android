package com.vishal.vibeplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {
    @GET("tracks/")
    suspend fun getTrendingTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 15, // Number of tracks for the horizontal scroll
        @Query("tags") tags: String = "trending" // Jamendo tag
    ): JamendoResponse // Make sure this matches your data class!

    // Add this right below your getTrendingTracks function
    @GET("albums/")
    suspend fun getTrendingAlbums(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 15,
        @Query("order") order: String = "popularity_week" // Gets the top albums!
    ): JamendoAlbumResponse

    // Fetches all tracks that belong to a specific Album ID
    // Using the dedicated album extraction endpoint
    @GET("albums/tracks/")
    suspend fun getTracksByAlbum(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("id") albumId: String // Notice this is now just 'id' instead of 'album_id'
    ): JamendoAlbumTracksResponse


}

