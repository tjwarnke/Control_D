package com.example.controld.data.model

import java.util.Date

data class PlaylistItem(
    val id: String,
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val addedAt: Date,
    val position: Int
)
