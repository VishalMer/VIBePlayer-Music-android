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
    ): JamendoResponse

    @GET("albums/")
    suspend fun getTrendingAlbums(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 15,
        @Query("order") order: String = "popularity_week" // Gets the top albums!
    ): JamendoAlbumResponse

    // THE FIX: We use the flat tracks endpoint and filter by album_id
    // This perfectly matches the JamendoResponse data class and our Fragment logic!
    @GET("tracks/")
    suspend fun getTracksByAlbum(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("album_id") albumId: String // Notice this is back to 'album_id'
    ): JamendoResponse

}