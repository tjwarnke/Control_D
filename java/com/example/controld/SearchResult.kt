package com.example.controld

import com.google.firebase.Timestamp

enum class SearchResultType {
    GAME, USER, LIST, STEAM_GAME
}

sealed class SearchResult {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val description: String
    abstract val imageUrl: String
    abstract val type: SearchResultType

    data class Game(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val description: String,
        override val imageUrl: String,
        override val type: SearchResultType = SearchResultType.GAME,
        val rating: Int = 0,
        val releaseDate: Timestamp? = null
    ) : SearchResult()

    data class User(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val description: String,
        override val imageUrl: String,
        override val type: SearchResultType = SearchResultType.USER,
        val joinDate: Timestamp? = null
    ) : SearchResult()

    data class List(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val description: String,
        override val imageUrl: String,
        override val type: SearchResultType = SearchResultType.LIST,
        val itemCount: Int = 0,
        val isPublic: Boolean = true,
        val createdAt: Timestamp? = null
    ) : SearchResult()
    
    data class SteamGame(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val description: String,
        override val imageUrl: String,
        override val type: SearchResultType = SearchResultType.STEAM_GAME,
        val appId: Int,
        val playtime: Int = 0,
        val lastPlayed: Int? = null,
        val developer: String = "",
        val publisher: String = "",
        val isOwned: Boolean = false,
        val releaseDate: String = "Release date unknown"
    ) : SearchResult()
} 