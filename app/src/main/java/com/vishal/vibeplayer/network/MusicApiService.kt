package com.vishal.vibeplayer.network

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {

    // We pass the client_id dynamically so we can fetch different genres/tags!
    @GET("tracks/")
    suspend fun getTrendingTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 20,
        @Query("tags") tags: String = "trending" // Can be "pop", "chill", "workout", etc.
    ): JamendoResponse
}