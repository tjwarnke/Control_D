package com.example.controld.steam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import retrofit2.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.google.gson.Gson

class SteamGameManager(private val steamApi: SteamApi) {
    companion object {
        private const val TAG = "SteamGameManager"
    }
    
    private val gson = Gson()
    
    // Cache for game details to reduce API calls
    private val gameDetailsCache = mutableMapOf<Int, GameDetailInfo>()
    
    data class GameDetailInfo(
        val appId: Int,
        val developer: String,
        val publisher: String
    )

    data class EnhancedSteamGame(
        val game: OwnedGame,
        val developer: String,
        val publisher: String
    )
    
    data class GameDetails(
        val name: String,
        val description: String,
        val developers: List<String>,
        val publishers: List<String>,
        val headerImage: String,
        val releaseDate: String
    )

    private fun validateSteamParameters(apiKey: String, steamId: String): Boolean {
        if (apiKey.isBlank()) {
            Log.e(TAG, "API Key is blank or empty")
            return false
        }
        if (steamId.isBlank()) {
            Log.e(TAG, "Steam ID is blank or empty")
            return false
        }
        if (!steamId.matches(Regex("^[0-9]{17}$"))) {
            Log.e(TAG, "Steam ID format is invalid. Expected 17 digits, got: $steamId")
            return false
        }
        return true
    }

    private suspend fun <T> handleApiCall(
        apiCall: suspend () -> Response<T>,
        operationName: String
    ): Response<T>? {
        return try {
            val response = apiCall()
            Log.d(TAG, "=== $operationName API Call ===")
            Log.d(TAG, "Status Code: ${response.code()}")
            Log.d(TAG, "Is Successful: ${response.isSuccessful}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error Response:")
                Log.e(TAG, "  Code: ${response.code()}")
                Log.e(TAG, "  Message: ${response.message()}")
                Log.e(TAG, "  Error Body: ${response.errorBody()?.string()}")
            }
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Exception during $operationName:", e)
            null
        }
    }

    private fun logResponseBody(response: Response<*>) {
        try {
            val responseBody = response.body()
            Log.d(TAG, "Response Body: ${gson.toJson(responseBody)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging response body", e)
        }
    }

    private fun logCacheStatus() {
        Log.d(TAG, "=== Game Details Cache Status ===")
        Log.d(TAG, "Total cached items: ${gameDetailsCache.size}")
        Log.d(TAG, "Cache contents:")
        gameDetailsCache.forEach { (appId, info) ->
            Log.d(TAG, "  App ID: $appId")
            Log.d(TAG, "    Developer: ${info.developer}")
            Log.d(TAG, "    Publisher: ${info.publisher}")
        }
    }

    private fun logImageUrlGeneration(appId: Int, logoHash: String?) {
        Log.d(TAG, "=== Image URL Generation ===")
        Log.d(TAG, "App ID: $appId")
        Log.d(TAG, "Logo Hash: $logoHash")
        Log.d(TAG, "Generated URL: ${getGameImageUrl(appId, logoHash)}")
    }

    private suspend fun <T> measureApiCall(
        operationName: String,
        apiCall: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        try {
            val result = apiCall()
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "=== Performance Metrics: $operationName ===")
            Log.d(TAG, "Duration: ${duration}ms")
            return result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Operation $operationName failed after ${duration}ms", e)
            throw e
        }
    }
    
    suspend fun getOwnedGames(apiKey: String, steamId: String): List<OwnedGame> {
        if (!validateSteamParameters(apiKey, steamId)) {
            return emptyList()
        }
        
        return measureApiCall("getOwnedGames") {
            handleApiCall(
                apiCall = { steamApi.getOwnedGames(apiKey, steamId, "json") },
                operationName = "Get Owned Games"
            )?.let { response ->
                if (response.isSuccessful) {
                    logResponseBody(response)
                    response.body()?.response?.games ?: emptyList()
                } else {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    suspend fun searchSteamStore(query: String): List<StoreSearchItem> {
        return measureApiCall("searchSteamStore") {
            handleApiCall(
                apiCall = { steamApi.searchGames(query) },
                operationName = "Search Steam Store"
            )?.let { response ->
                if (response.isSuccessful) {
                    logResponseBody(response)
                    response.body()?.items ?: emptyList()
                } else {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    suspend fun enhanceGamesWithDetails(apiKey: String, games: List<OwnedGame>): List<EnhancedSteamGame> {
        logCacheStatus()
        return measureApiCall("enhanceGamesWithDetails") {
            games.map { game ->
                val details = getGameDetailInfo(apiKey, game.appid)
                EnhancedSteamGame(
                    game = game,
                    developer = details.developer,
                    publisher = details.publisher
                )
            }
        }
    }

    fun getGameImageUrl(appId: Int, logoHash: String?): String {
        logImageUrlGeneration(appId, logoHash)
        return if (!logoHash.isNullOrEmpty()) {
            "https://media.steampowered.com/steamcommunity/public/images/apps/$appId/$logoHash.jpg"
        } else {
            "https://steamcdn-a.akamaihd.net/steam/apps/$appId/header.jpg"
        }
    }

    fun formatPlaytime(minutes: Int): String {
        val hours = minutes / 60
        return when {
            hours == 0 -> "Less than 1 hour"
            hours == 1 -> "1 hour"
            else -> "$hours hours"
        }
    }

    /**
     * Get developer and publisher information for a Steam game
     */
    suspend fun getGameDetailInfo(apiKey: String, appId: Int): GameDetailInfo {
        // Check cache first
        gameDetailsCache[appId]?.let {
            Log.d(TAG, "Cache hit for appId: $appId")
            return it
        }
        
        Log.d(TAG, "Cache miss for appId: $appId")
        return measureApiCall("getGameDetailInfo") {
            GameDetailInfo(
                appId = appId,
                developer = "",  // Would come from API
                publisher = ""   // Would come from API
            ).also {
                gameDetailsCache[appId] = it
                logCacheStatus()
            }
        }
    }

    suspend fun getGameDetails(appId: Long): GameDetails? {
        return measureApiCall("getGameDetails") {
            handleApiCall(
                apiCall = { steamApi.getGameDetails(appId) },
                operationName = "Get Game Details"
            )?.let { response ->
                if (response.isSuccessful) {
                    logResponseBody(response)
                    val gameDetails = response.body()
                    if (gameDetails != null && gameDetails.isNotEmpty()) {
                        val appIdStr = appId.toString()
                        val details = gameDetails[appIdStr]
                        if (details?.success == true && details.data != null) {
                            val data = details.data
                            Log.d(TAG, "Game data release date: ${data.release_date?.date}")
                            GameDetails(
                                name = data.name ?: "Unknown Game",
                                description = data.short_description ?: "No description available",
                                developers = data.developers ?: emptyList(),
                                publishers = data.publishers ?: emptyList(),
                                headerImage = data.header_image ?: "https://via.placeholder.com/460x215",
                                releaseDate = data.release_date?.date ?: "Release date unknown"
                            )
                        } else {
                            Log.e(TAG, "Failed to get game details for appId $appId: ${details?.errorMessage}")
                            createFallbackGameDetails(appId)
                        }
                    } else {
                        Log.e(TAG, "Empty response for appId $appId")
                        createFallbackGameDetails(appId)
                    }
                } else {
                    Log.e(TAG, "API call failed for appId $appId: ${response.code()} - ${response.message()}")
                    createFallbackGameDetails(appId)
                }
            } ?: createFallbackGameDetails(appId)
        }
    }

    private fun createFallbackGameDetails(appId: Long): GameDetails {
        Log.w(TAG, "Creating fallback game details for appId: $appId")
        return GameDetails(
            name = "Unknown Game",
            description = "No description available",
            developers = emptyList(),
            publishers = emptyList(),
            headerImage = "https://via.placeholder.com/460x215",
            releaseDate = "Release date unknown"
        )
    }

    suspend fun getGameStats(apiKey: String, steamId: String, appId: Int): GameStatsResponse? {
        if (!validateSteamParameters(apiKey, steamId)) {
            return null
        }

        return measureApiCall("getGameStats") {
            handleApiCall(
                apiCall = { steamApi.getGameStats(apiKey, steamId, appId) },
                operationName = "Get Game Stats"
            )?.let { response ->
                if (response.isSuccessful) {
                    logResponseBody(response)
                    response.body()?.response
                } else {
                    null
                }
            }
        }
    }
} 