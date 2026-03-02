package com.vishal.vibeplayer.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // TODO: Replace this with your actual API server URL when you have one!
    // NOTE: It MUST end with a trailing slash "/"
    private const val BASE_URL = "https://api.jamendo.com/v3.0/"

    val apiService: MusicApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Uses Gson to parse the JSON
            .build()
            .create(MusicApiService::class.java)
    }
}