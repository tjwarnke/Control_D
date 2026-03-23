package com.example.controld

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.controld.steam.SteamApi
import com.example.controld.steam.SteamApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class SteamAuthActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val steamApi: SteamApi = SteamApiClient.create()
    private val TAG = "SteamAuth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steam_auth)

        Log.d(TAG, "SteamAuthActivity started")
        
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                Log.d(TAG, "URL loading: $url")
                
                // Check if this is our callback URL
                if (url.contains("steamcommunity.com/openid/login") && url.contains("openid.mode=id_res")) {
                    Log.d(TAG, "Detected successful Steam authentication response")
                    // This is the response from Steam with the identity
                    extractSteamId(url)
                    return true
                }
                return false
            }
            
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView error: $errorCode - $description, URL: $failingUrl")
            }
        }

        // Start Steam OpenID authentication
        // We'll use the mobile Steam login page directly
        val steamOpenIdUrl = "https://steamcommunity.com/openid/login?" +
                "openid.ns=http://specs.openid.net/auth/2.0&" +
                "openid.mode=checkid_setup&" +
                "openid.return_to=https://steamcommunity.com/openid/login&" +
                "openid.realm=https://steamcommunity.com&" +
                "openid.identity=http://specs.openid.net/auth/2.0/identifier_select&" +
                "openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select"

        Log.d(TAG, "Loading Steam authentication URL: $steamOpenIdUrl")
        webView.loadUrl(steamOpenIdUrl)
    }

    private fun extractSteamId(url: String) {
        Log.d(TAG, "Processing response URL: $url")
        val uri = url.toUri()
        
        // Extract the claimed_id parameter which contains the Steam ID
        val claimedId = uri.getQueryParameter("openid.claimed_id") ?: ""
        val steamId = claimedId.substringAfterLast('/')
        
        Log.d(TAG, "Extracted Steam ID: $steamId")
        
        if (steamId.isNotEmpty()) {
            fetchSteamProfile(steamId)
        } else {
            Log.e(TAG, "Failed to extract Steam ID from response URL: $url")
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun fetchSteamProfile(steamId: String) {
        Log.d(TAG, "Fetching Steam profile for ID: $steamId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = steamApi.getPlayerSummaries(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    steamIds = steamId
                )
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val player = response.body()?.response?.players?.firstOrNull()
                        if (player != null) {
                            Log.d(TAG, "Retrieved player info: ${player.personaname}")
                            // Return to main activity with success
                            val resultIntent = Intent().apply {
                                putExtra("steam_id", player.steamid)
                                putExtra("steam_name", player.personaname)
                                putExtra("steam_avatar", player.avatarfull)
                            }
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            Log.e(TAG, "No player data found in response")
                            setResult(RESULT_CANCELED)
                        }
                    } else {
                        Log.e(TAG, "API call failed: ${response.code()} - ${response.errorBody()?.string()}")
                        setResult(RESULT_CANCELED)
                    }
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call", e)
                withContext(Dispatchers.Main) {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
} 