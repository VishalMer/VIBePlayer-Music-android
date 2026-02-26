package com.vishal.vibeplayer.model

import android.graphics.Bitmap

data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val art: Bitmap? = null // It is nullable (?) because some MP3s don't have art!
)