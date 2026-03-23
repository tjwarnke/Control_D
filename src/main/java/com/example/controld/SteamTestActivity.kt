package com.example.controld

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.controld.steam.SteamApi
import com.example.controld.steam.SteamApiClient
import com.example.controld.steam.SteamGameManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SteamTestActivity : AppCompatActivity() {
    private lateinit var steamApi: SteamApi
    private lateinit var steamGameManager: SteamGameManager
    private lateinit var statusText: TextView
    private lateinit var gamesText: TextView
    private lateinit var connectButton: Button
    private lateinit var fetchGamesButton: Button

    private val steamAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val steamId = result.data?.getStringExtra("steam_id")
            val steamName = result.data?.getStringExtra("steam_name")
            statusText.text = "Connected as: $steamName ($steamId)"
            fetchGamesButton.isEnabled = true
        } else {
            statusText.text = "Steam authentication failed"
        }
    }

    companion object {
        private const val STEAM_AUTH_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steam_test)

        // Initialize views
        statusText = findViewById(R.id.status_text)
        gamesText = findViewById(R.id.games_text)
        connectButton = findViewById(R.id.connect_button)
        fetchGamesButton = findViewById(R.id.fetch_games_button)

        // Initialize Steam API
        steamApi = SteamApiClient.create()
        steamGameManager = SteamGameManager(steamApi)

        // Set up button click listeners
        connectButton.setOnClickListener {
            startSteamAuth()
        }

        fetchGamesButton.setOnClickListener {
            fetchSteamGames()
        }
    }

    private fun startSteamAuth() {
        val intent = Intent(this, SteamAuthActivity::class.java)
        steamAuthLauncher.launch(intent)
    }

    private fun fetchSteamGames() {
        // For testing, we'll use a sample Steam ID
        val steamId = "76561198012345678" // Replace with actual Steam ID after authentication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val games = steamGameManager.getOwnedGames(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    steamId = steamId
                )
                
                withContext(Dispatchers.Main) {
                    if (games.isNotEmpty()) {
                        val gamesList = games.joinToString("\n") { game ->
                            "${game.name} - ${steamGameManager.formatPlaytime(game.playtime_forever)}"
                        }
                        gamesText.text = gamesList
                        statusText.text = "Successfully fetched ${games.size} games"
                    } else {
                        statusText.text = "No games found or error occurred"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error: ${e.message}"
                }
            }
        }
    }
} 