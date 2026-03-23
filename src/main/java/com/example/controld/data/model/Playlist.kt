package com.example.controld.data.model

import java.util.Date

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val coverUrl: String?,
    val itemCount: Int,
    val createdAt: Date,
    val updatedAt: Date
)