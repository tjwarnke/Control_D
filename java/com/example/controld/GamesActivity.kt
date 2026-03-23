package com.example.controld

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.controld.steam.SteamApi
import com.example.controld.steam.SteamApiClient
import com.example.controld.steam.SteamGameManager
import com.example.controld.ui.games.GamesFragment
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class GamesActivity : AbstractListActivity() {
    private val TAG = "GamesActivity"
    private var userID = "null"
    private var steamId: String? = null
    private var isSteamConnected = false
    private val firestore = Firebase.firestore
    private val steamApi = SteamApiClient.create()
    private val steamGameManager = SteamGameManager(steamApi)
    
    override fun getTitleString(): String {
        return getString(R.string.my_games)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Get the user ID
        val sharedPref = getSharedPreferences(getString(R.string.accountPrefKey), Context.MODE_PRIVATE)
        userID = sharedPref.getString(getString(R.string.emailKey), "null") ?: "null"
        
        super.onCreate(savedInstanceState)
    }
    
    override fun loadData() {
        // First check if user has Steam connected
        CoroutineScope(Dispatchers.Main).launch {
            checkSteamConnection()
        }
    }
    
    private suspend fun checkSteamConnection() {
        try {
            if (userID == "null") {
                loadDefaultGames()
                return
            }
            
            val document = withContext(Dispatchers.IO) {
                firestore.collection("users").document(userID).get().await()
            }
            
            steamId = document.getString("steam_id")
            if (!steamId.isNullOrEmpty()) {
                isSteamConnected = true
                loadSteamGames()
            } else {
                // User doesn't have Steam connected
                showNotConnectedMessage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Steam connection", e)
            loadDefaultGames()
        }
    }
    
    private fun showNotConnectedMessage() {
        val notConnectedItem = ActivityItem(
            name = "No Steam Connected",
            gameTitle = "Connect your Steam account in your profile to see your games",
            type = "",
            avatarUrl = "",
            id = "",
            secondId = "",
            date = Date()
        )
        adapter.submitList(listOf(notConnectedItem))
    }
    
    private suspend fun loadSteamGames() {
        try {
            val steamGames = withContext(Dispatchers.IO) {
                val ownedGames = steamGameManager.getOwnedGames(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    steamId = steamId ?: ""
                )
                
                // Convert to ActivityItem objects
                val gamesList = mutableListOf<ActivityItem>()
                ownedGames.forEach { game ->
                    val playtime = game.playtime_forever / 60 // Convert minutes to hours
                    // Use rtime_last_played as date if available, otherwise use current time
                    val lastPlayed = game.rtime_last_played?.let { 
                        if (it > 0) Date(it * 1000L) else Date() 
                    } ?: Date()
                    
                    gamesList.add(
                        ActivityItem(
                            name = game.name,
                            gameTitle = "Steam Game",
                            type = if (playtime > 0) "${playtime}h played" else "Not played yet",
                            avatarUrl = "https://steamcdn-a.akamaihd.net/steam/apps/${game.appid}/header.jpg",
                            id = game.appid.toString(),
                            secondId = "",
                            date = lastPlayed
                        )
                    )
                }
                
                return@withContext gamesList
            }
            
            // Apply sorting outside of IO context
            val sortedGames = when (currentSortType) {
                SortType.NEWEST -> steamGames.sortedByDescending { it.date }
                SortType.OLDEST -> steamGames.sortedBy { it.date }
                SortType.NAME_ASC -> steamGames.sortedBy { it.name }
                SortType.NAME_DESC -> steamGames.sortedByDescending { it.name }
                else -> steamGames
            }
            
            adapter.submitList(sortedGames)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Steam games", e)
            loadDefaultGames()
        }
    }
    
    private fun loadDefaultGames() {
        // Load some default placeholder games
        val defaultGames = listOf(
            ActivityItem(
                name = "No games found",
                gameTitle = "Steam connection required",
                type = "Connect Steam in your profile",
                avatarUrl = "",
                id = "",
                secondId = ""
            )
        )
        adapter.submitList(defaultGames)
    }
    
    override fun handleUserData(dataType: String, data: ActivityItem) {
        // Handle game selection if needed
        // For now we'll just show a Toast with the game name
        if (data.id.isNotEmpty()) {
            // Launch game detail activity or fragment here
        }
    }
    
    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, GamesActivity::class.java)
        }
    }
} 