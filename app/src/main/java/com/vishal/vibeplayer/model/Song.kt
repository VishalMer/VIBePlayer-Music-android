package com.vishal.vibeplayer.model

import android.graphics.Bitmap

data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val path: String, // We added this! This tells the player where the file is on the phone.
    val art: Bitmap? = null
)