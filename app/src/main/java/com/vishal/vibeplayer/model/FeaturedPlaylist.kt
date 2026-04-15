package com.vishal.vibeplayer.model

data class FeaturedPlaylist(
    val specialId: Int,       // The negative ID for routing
    val title: String,        // "Liked Songs", "Offline Mix", etc.
    val iconRes: Int,         // The center icon
    val backgroundRes: Int    // The gradient background
)