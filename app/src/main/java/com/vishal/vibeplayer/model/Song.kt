package com.vishal.vibeplayer.model

import android.graphics.Bitmap

data class Song(
    // Existing fields for Local Mode
    val title: String,
    val artist: String,
    val duration: String,
    val path: String,
    var art: Bitmap? = null,     // RESTORED: This holds your local album art!

    // --- NEW FIELDS FOR ONLINE MODE ---
    val id: String = "",         // The unique ID from your API
    val audioUrl: String = "",   // The streaming link (.mp3 online)
    val imageUrl: String = "",   // The album cover link from the web
    val isOnline: Boolean = false // The Global Flag to tell the player which mode to use!
)