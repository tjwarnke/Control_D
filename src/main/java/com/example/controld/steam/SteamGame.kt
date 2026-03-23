package com.example.controld.steam

data class SteamGame(
    val appid: Long,
    val name: String,
    val playtime_forever: Int,
    val img_icon_url: String,
    val img_logo_url: String,
    val has_community_visible_stats: Boolean,
    val playtime_windows_forever: Int,
    val playtime_mac_forever: Int,
    val playtime_linux_forever: Int
)

data class SteamUserReview(
    val steamid: String,
    val appid: Long,
    val review: String,
    val timestamp: Long,
    val recommended: Boolean
) 