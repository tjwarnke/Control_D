package com.example.controld.steam

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SteamApi {
    @GET("IPlayerService/GetOwnedGames/v1/")
    suspend fun getOwnedGames(
        @Query("key") apiKey: String,
        @Query("steamid") steamId: String,
        @Query("format") format: String = "json",
        @Query("include_appinfo") includeAppInfo: Boolean = true,
        @Query("include_played_free_games") includeFreeGames: Boolean = true
    ): Response<SteamResponse<OwnedGamesResponse>>

    @GET("ISteamUserStats/GetUserStatsForGame/v2/")
    suspend fun getGameStats(
        @Query("key") apiKey: String,
        @Query("steamid") steamId: String,
        @Query("appid") appId: Int
    ): Response<SteamResponse<GameStatsResponse>>

    @GET("ISteamUser/GetPlayerSummaries/v2/")
    suspend fun getPlayerSummaries(
        @Query("key") apiKey: String,
        @Query("steamids") steamIds: String
    ): Response<SteamResponse<PlayerSummariesResponse>>

    @GET("https://store.steampowered.com/api/storesearch/")
    suspend fun searchGames(
        @Query("term") searchTerm: String,
        @Query("l") language: String = "english",
        @Query("cc") countryCode: String = "US"
    ): Response<StoreSearchResponse>

    @GET("https://store.steampowered.com/api/appdetails")
    suspend fun getGameDetails(
        @Query("appids") appId: Long,
        @Query("l") language: String = "english",
        @Query("cc") countryCode: String = "US",
        @Query("filters") filters: String = "basic,developers,publishers,release_date,description"
    ): Response<Map<String, SteamGameDetails>>
}

data class SteamResponse<T>(
    val response: T
)

data class OwnedGamesResponse(
    val game_count: Int,
    val games: List<OwnedGame>
)

data class OwnedGame(
    val appid: Int,
    val name: String,
    val playtime_forever: Int,
    val img_icon_url: String,
    val img_logo_url: String?,
    val rtime_last_played: Int? = 0
)

data class GameStatsResponse(
    val playerstats: PlayerStats
)

data class PlayerStats(
    val steamID: String,
    val gameName: String,
    val stats: List<Stat>,
    val achievements: List<Achievement>
)

data class Stat(
    val name: String,
    val value: Int
)

data class Achievement(
    val name: String,
    val achieved: Int
)

data class PlayerSummariesResponse(
    val players: List<PlayerSummary>
)

data class PlayerSummary(
    val steamid: String,
    val personaname: String,
    val profileurl: String,
    val avatar: String,
    val avatarmedium: String,
    val avatarfull: String,
    val personastate: Int,
    val communityvisibilitystate: Int,
    val profilestate: Int,
    val lastlogoff: Long,
    val commentpermission: Int
)

data class StoreSearchResponse(
    val success: Boolean,
    val total: Int,
    val items: List<StoreSearchItem>
)

data class StoreSearchItem(
    val id: Int,
    val type: String,
    val name: String,
    val discounted: Boolean,
    val discount_percent: Int,
    val original_price: Int?,
    val final_price: Int?,
    val currency: String?,
    val large_capsule_image: String?,
    val small_capsule_image: String?,
    val windows_available: Boolean,
    val mac_available: Boolean,
    val linux_available: Boolean,
    val streamingvideo_available: Boolean,
    val header_image: String?,
    val controller_support: String?,
    val release_date: String?
)

data class SteamGameDetails(
    val success: Boolean,
    val data: GameData?,
    val errorMessage: String? = null
)

data class GameData(
    val name: String?,
    val short_description: String?,
    val developers: List<String>?,
    val publishers: List<String>?,
    val header_image: String?,
    val release_date: ReleaseDate?
)

data class Requirements(
    val minimum: String,
    val recommended: String
)

data class PriceOverview(
    val currency: String,
    val initial: Int,
    val final: Int,
    val discount_percent: Int,
    val initial_formatted: String,
    val final_formatted: String
)

data class PackageGroup(
    val name: String,
    val title: String,
    val description: String,
    val selection_text: String,
    val save_text: String,
    val display_type: Int,
    val is_recurring_subscription: String,
    val subs: List<Sub>
)

data class Sub(
    val packageid: Long,
    val percent_savings_text: String,
    val percent_savings: Int,
    val option_text: String,
    val option_description: String,
    val can_get_free_license: String,
    val is_free_license: Boolean,
    val price_in_cents_with_discount: Long
)

data class Platforms(
    val platforms: List<Platform>
)

data class Platform(
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean
)

data class Metacritic(
    val score: Int,
    val url: String
)

data class Category(
    val id: Int,
    val description: String
)

data class Genre(
    val id: String,
    val description: String
)

data class Screenshot(
    val id: Int,
    val path_thumbnail: String,
    val path_full: String
)

data class Movie(
    val id: Int,
    val name: String,
    val thumbnail: String,
    val webm: Webm,
    val mp4: Mp4,
    val highlight: Boolean
)

data class Webm(
    val video: String,
    val mp4: String,
    val webp: String
)

data class Mp4(
    val video: String,
    val mp4: String,
    val webp: String
)

data class Recommendations(
    val total: Int
)

data class Achievements(
    val total: Int,
    val highlighted: List<HighlightedAchievement>
)

data class HighlightedAchievement(
    val name: String,
    val path: String
)

data class ReleaseDate(
    val coming_soon: Boolean,
    val date: String
)

data class SupportInfo(
    val url: String,
    val email: String
)

data class ContentDescriptors(
    val ids: List<Int>,
    val notes: String
) 