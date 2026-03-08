package com.vishal.vibeplayer.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.jamendo.com/v3.0/"

    // We keep this null initially so we can pass the Context (phone's storage) in later
    private var retrofit: Retrofit? = null

    // Now requires a Context so it knows exactly where the cache folder is
    fun getApiService(context: Context): MusicApiService {
        if (retrofit == null) {

            // 1. Allocate 5MB of the phone's storage specifically for saving API data
            val cacheSize = (5 * 1024 * 1024).toLong()
            val myCache = Cache(context.cacheDir, cacheSize)

            // 2. Build the Bouncer!
            val okHttpClient = OkHttpClient.Builder()
                .cache(myCache)
                .addInterceptor { chain ->
                    var request = chain.request()

                    // Tell the app: "If this data is less than 5 minutes old (300 seconds), use the cache!"
                    request = request.newBuilder()
                        .header("Cache-Control", "public, max-age=" + 60 * 5)
                        .build()

                    chain.proceed(request)
                }
                .build()

            // 3. Attach the smart client to Retrofit
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit!!.create(MusicApiService::class.java)
    }
}