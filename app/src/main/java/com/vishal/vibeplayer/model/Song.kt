package com.vishal.vibeplayer.model

import android.graphics.Bitmap

data class Song(
    val title: String,
    val artist: String,
    val duration: String,
    val path: String, // Acts as local file path OR the Jamendo Album/Stream ID
    val art: Bitmap? = null, // For offline local images
    val isOnline: Boolean = false, // Tells the app if this is an internet song
    val imageUrl: String? = null // For online Jamendo image URLs
)