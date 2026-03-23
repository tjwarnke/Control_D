package com.example.controld

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.getField
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.controld.steam.SteamApi
import com.example.controld.steam.SteamApiClient
import com.example.controld.steam.SteamGameManager
import com.example.controld.steam.OwnedGame
import android.content.SharedPreferences
import androidx.fragment.app.commit
import androidx.compose.ui.text.toLowerCase

class SearchFragment : Fragment() {
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var recentSearchesRecyclerView: RecyclerView
    private lateinit var popularSearchesRecyclerView: RecyclerView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var recentSearchesTitle: TextView
    private lateinit var popularSearchesTitle: TextView
    private lateinit var loadingIndicator: View
    
    // Arguments for adding to list functionality
    private var listId: String? = null
    private var addToListMode: Boolean = false

    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private lateinit var popularSearchAdapter: PopularSearchAdapter
    private lateinit var searchResultsAdapter: SearchResultAdapter

    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private val recentSearches = mutableListOf<String>()
    private val popularSearches = mutableListOf<String>()
    private val searchResults = mutableListOf<SearchResult>()
    
    // Steam integration
    private val steamApi: SteamApi = SteamApiClient.create()
    private val steamGameManager = SteamGameManager(steamApi)
    private var steamId: String? = null
    private var isSteamConnected = false

    private var searchJob: Job? = null
    private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments if we're in add-to-list mode
        arguments?.let { args ->
            listId = args.getString("list_id")
            addToListMode = args.getBoolean("add_to_list_mode", false)
        }

        // Initialize views
        searchView = view.findViewById(R.id.searchView)
        filterChipGroup = view.findViewById(R.id.filterChipGroup)
        recentSearchesRecyclerView = view.findViewById(R.id.recentSearchesRecyclerView)
        popularSearchesRecyclerView = view.findViewById(R.id.popularSearchesRecyclerView)
        searchResultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        recentSearchesTitle = view.findViewById(R.id.recentSearchesTitle)
        popularSearchesTitle = view.findViewById(R.id.popularSearchesTitle)
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        
        // Find and initially hide the Steam chip
        val steamChip = view.findViewById<Chip>(R.id.chipSteam)
        steamChip?.visibility = View.GONE

        // Load Steam info for current user - this will show the chip if Steam is connected
        loadSteamInfo()

        // Initialize adapters
        recentSearchAdapter = RecentSearchAdapter(
            onSearchClick = { query -> performSearch(query) },
            onClearClick = { query -> removeRecentSearch(query) }
        )
        popularSearchAdapter = PopularSearchAdapter { query -> performSearch(query) }
        
        // Create the search results adapter, passing the list ID if available
        searchResultsAdapter = SearchResultAdapter(
            onItemClick = { result -> handleSearchResultClick(result) },
            preferredListId = listId,
            isAddToListMode = addToListMode
        )
        
        // If we're in add-to-list mode, show a toast to guide the user
        if (addToListMode && listId != null) {
            Toast.makeText(
                context,
                "Search for games to add to your list",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Setup RecyclerViews
        recentSearchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentSearchAdapter
        }
        popularSearchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = popularSearchAdapter
        }
        searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultsAdapter
        }

        // Setup search functionality with debounce
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { 
                    Log.d("Steam Search", "Search submitted: $it")
                    performSearch(it) 
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { debouncedSearch(it) }
                return true
            }
        })

        // Setup filter chips
        filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            // Clear current results
            searchResults.clear()
            searchResultsAdapter.submitList(emptyList())
            
            Log.d("Steam Search", "Filter changed to ID: $checkedId")
            
            // Perform new search with current query if exists
            searchView.query?.toString()?.let { query ->
                if (query.isNotBlank()) {
                    Log.d("Steam Search", "Repeating search with new filter: $query")
                    performSearch(query)
                }
            }
        }

        // Load initial data
        loadRecentSearches()
        loadPopularSearches()
    }
    
    // Load Steam info for the current user
    private fun loadSteamInfo() {
        val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey), Context.MODE_PRIVATE)
        val userID = sharedPref?.getString(getString(R.string.emailKey), "null") ?: "null"
        
        // Debug logging
        Log.d("Steam Search", "Loading Steam info for user: $userID")
        
        // Find the Steam chip now so we can reference it
        val steamChip = view?.findViewById<Chip>(R.id.chipSteam)
        
        // Check if Steam is connected for this user
        val userRef = Firebase.firestore.collection("users").document(userID)
        userRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val storedSteamId = document.getString("steam_id")
                if (!storedSteamId.isNullOrEmpty()) {
                    Log.d("Steam Search", "Found Steam ID for current user: $storedSteamId")
                    steamId = storedSteamId
                    isSteamConnected = true
                    
                    // Make the Steam filter chip visible
                    steamChip?.visibility = View.VISIBLE
                    Log.d("Steam Search", "Made Steam chip visible")
                    
                    // Pre-fetch Steam games for faster search results
                    prefetchSteamGames()
                } else {
                    Log.d("Steam Search", "No Steam ID found for user")
                    steamChip?.visibility = View.GONE
                }
            } else {
                Log.d("Steam Search", "User document not found")
                steamChip?.visibility = View.GONE
            }
        }.addOnFailureListener { e ->
            Log.e("Steam Search", "Error loading user data: ${e.message}")
            steamChip?.visibility = View.GONE
        }
    }
    
    // Pre-fetch Steam games to cache them
    private fun prefetchSteamGames() {
        if (!isSteamConnected || steamId.isNullOrEmpty()) {
            Log.d("Steam Search", "Cannot prefetch: Steam not connected")
            return
        }
        
        searchScope.launch(Dispatchers.IO) {
            try {
                Log.d("Steam Search", "Pre-fetching Steam games")
                val steamGames = steamGameManager.getOwnedGames(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    steamId = steamId ?: ""
                )
                Log.d("Steam Search", "Pre-fetched ${steamGames.size} Steam games")
            } catch (e: Exception) {
                Log.e("Steam Search", "Error pre-fetching Steam games: ${e.message}")
            }
        }
    }

    private fun debouncedSearch(query: String) {
        // Cancel any existing search
        searchJob?.cancel()
        
        // Start a new search after a delay
        searchJob = searchScope.launch {
            delay(300) // 300ms debounce
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults.clear()
            searchResultsAdapter.submitList(emptyList())
            searchResultsRecyclerView.visibility = View.GONE
            recentSearchesTitle.visibility = View.VISIBLE
            popularSearchesTitle.visibility = View.VISIBLE
            return
        }

        loadingIndicator.visibility = View.VISIBLE
        searchResultsRecyclerView.visibility = View.VISIBLE
        recentSearchesTitle.visibility = View.GONE
        popularSearchesTitle.visibility = View.GONE

        // Clear previous results
        searchResults.clear()
        searchResultsAdapter.submitList(emptyList())

        val selectedChipId = filterChipGroup.checkedChipId
        Log.d("Steam Search", "Selected chip ID: $selectedChipId")
        
        when (selectedChipId) {
            R.id.chipGames -> {
                Log.d("Steam Search", "Games filter selected")
                searchGames(query)
                // Also search Steam games when games filter is selected
                if (isSteamConnected) {
                    Log.d("Steam Search", "Including Steam games in Games filter")
                    searchSteamGames(query)
                }
            }
            R.id.chipSteam -> {
                Log.d("Steam Search", "Steam filter selected")
                // Only search Steam games when Steam filter is selected
                if (isSteamConnected) {
                    searchSteamGames(query)
                } else {
                    // If somehow Steam filter is selected but not connected
                    Toast.makeText(context, "Not connected to Steam", Toast.LENGTH_SHORT).show()
                    Log.d("Steam Search", "Steam filter selected but not connected")
                }
            }
            R.id.chipUsers -> {
                Log.d("Steam Search", "Users filter selected")
                searchUsers(query)
            }
            R.id.chipLists -> {
                Log.d("Steam Search", "Lists filter selected")
                searchLists(query)
            }
            R.id.chipGenres -> {
                Log.d("Genres Search", "Lists filter selected")
                searchGenres(query)
            }
            else -> {
                Log.d("Steam Search", "All or no filter selected (ID: $selectedChipId)")
                searchGames(query)
                searchUsers(query)
                searchLists(query)
                searchGenres(query)
                // Also search Steam games when no specific filter is selected
                if (isSteamConnected) {
                    Log.d("Steam Search", "Including Steam games in All filter")
                    searchSteamGames(query)
                }
            }
        }

        addRecentSearch(query)
    }

    private fun searchGames(query: String) {
        searchScope.launch(Dispatchers.IO) {
            try {
                val title_query = query.capitaliseEachWord()
                val gamesSnapshot = db.collection("games")
                    .whereGreaterThanOrEqualTo("name", title_query)
                    .whereLessThanOrEqualTo("name", title_query + "\uf8ff")
                    .limit(5)
                    .get()
                    .await()

                val gameResults = gamesSnapshot.documents.map { doc ->
                    SearchResult.Game(
                        id = doc.id,
                        title = doc.getString("name") ?: "",
                        subtitle = doc.getString("publisher") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("coverImageUrl") ?: "",
                        rating = doc.getLong("rating")?.toInt() ?: 0,
                        releaseDate = doc.getTimestamp("releaseDate")
                    )
                }

                withContext(Dispatchers.Main) {
                    searchResults.addAll(gameResults)
                    searchResultsAdapter.submitList(searchResults.toList())
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error searching games", e)
                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    private fun searchSteamGames(query: String) {
        if (!isSteamConnected || steamId.isNullOrEmpty()) {
            Log.d("Steam Search", "Steam not connected, skipping Steam game search")
            return
        }

        loadingIndicator.visibility = View.VISIBLE
        Log.d("Steam Search", "Starting Steam game search for query: '$query'")
        
        searchScope.launch(Dispatchers.IO) {
            try {
                // Search both owned games and Steam store in parallel
                val (ownedGames, storeGames) = coroutineScope {
                    val ownedGamesDeferred = async {
                        val allOwnedGames = steamGameManager.getOwnedGames(
                            apiKey = BuildConfig.STEAM_API_KEY,
                            steamId = steamId ?: ""
                        )
                        // Filter owned games by query
                        allOwnedGames.filter { 
                            it.name.lowercase().contains(query.lowercase())
                        }
                    }
                    
                    val storeGamesDeferred = async {
                        steamGameManager.searchSteamStore(query)
                    }
                    
                    Pair(ownedGamesDeferred.await(), storeGamesDeferred.await())
                }
                
                Log.d("Steam Search", "Found ${ownedGames.size} owned games and ${storeGames.size} store games matching '$query'")
                
                if (ownedGames.isEmpty() && storeGames.isEmpty()) {
                    Log.d("Steam Search", "No Steam games match the query '$query'")
                    withContext(Dispatchers.Main) {
                        loadingIndicator.visibility = View.GONE
                        // Only show Toast if specifically searching for Steam games
                        if (filterChipGroup.checkedChipId == R.id.chipSteam) {
                            Toast.makeText(context, "No Steam games found matching '$query'", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                // Convert owned games to search results
                val ownedGameResults = steamGameManager.enhanceGamesWithDetails(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    games = ownedGames
                ).map { enhancedGame ->
                    val game = enhancedGame.game
                    val subtitle = when {
                        enhancedGame.developer.isNotEmpty() && enhancedGame.publisher.isNotEmpty() -> 
                            "${enhancedGame.developer} • ${enhancedGame.publisher}".trim()
                        enhancedGame.developer.isNotEmpty() -> enhancedGame.developer
                        enhancedGame.publisher.isNotEmpty() -> enhancedGame.publisher
                        else -> "Steam Game (Owned)"
                    }
                    
                    SearchResult.SteamGame(
                        id = game.appid.toString(),
                        title = game.name,
                        subtitle = subtitle,
                        description = "${steamGameManager.formatPlaytime(game.playtime_forever)} played",
                        imageUrl = steamGameManager.getGameImageUrl(game.appid, game.img_logo_url),
                        appId = game.appid,
                        playtime = game.playtime_forever,
                        lastPlayed = game.rtime_last_played,
                        developer = enhancedGame.developer,
                        publisher = enhancedGame.publisher,
                        isOwned = true
                    )
                }

                // Convert store games to search results
                val storeGameResults = storeGames.map { storeGame ->
                    Log.d("Steam Search", "Processing store game: ${storeGame.name}")
                    Log.d("Steam Search", "Store game release date: ${storeGame.release_date}")
                    
                    // Fetch detailed game information
                    val gameDetails = steamGameManager.getGameDetails(storeGame.id.toLong())
                    Log.d("Steam Search", "Game details fetched - success: ${gameDetails != null}")
                    Log.d("Steam Search", "Game details release date: ${gameDetails?.releaseDate}")
                    
                    // Ensure we have a valid image URL
                    val imageUrl = when {
                        !storeGame.header_image.isNullOrEmpty() -> storeGame.header_image
                        !storeGame.small_capsule_image.isNullOrEmpty() -> storeGame.small_capsule_image
                        !storeGame.large_capsule_image.isNullOrEmpty() -> storeGame.large_capsule_image
                        else -> "https://steamcdn-a.akamaihd.net/steam/apps/${storeGame.id}/header.jpg"
                    }
                    
                    val releaseDate = gameDetails?.releaseDate ?: "Release date unknown"
                    Log.d("Steam Search", "Final release date being used: $releaseDate")
                    
                    SearchResult.SteamGame(
                        id = storeGame.id.toString(),
                        title = storeGame.name,
                        subtitle = gameDetails?.publishers?.firstOrNull() ?: "Steam Store",
                        description = gameDetails?.description ?: "",
                        imageUrl = imageUrl,
                        appId = storeGame.id,
                        playtime = 0,
                        lastPlayed = null,
                        developer = gameDetails?.developers?.firstOrNull() ?: "",
                        publisher = gameDetails?.publishers?.firstOrNull() ?: "",
                        isOwned = false,
                        releaseDate = releaseDate
                    )
                }

                // Combine and deduplicate results (prefer owned games if duplicate)
                val combinedResults = (ownedGameResults + storeGameResults)
                    .distinctBy { it.appId }
                    .sortedWith(
                        compareByDescending<SearchResult.SteamGame> { it.isOwned }
                        .thenBy { it.title }
                    )

                withContext(Dispatchers.Main) {
                    if (combinedResults.isNotEmpty()) {
                        Log.d("Steam Search", "Adding ${combinedResults.size} Steam games to search results")
                        searchResults.addAll(combinedResults)
                        val updatedList = searchResults.toList()
                        searchResultsAdapter.submitList(updatedList)
                        
                        // Force update the UI
                        searchResultsAdapter.notifyDataSetChanged()
                        
                        // Make sure results are visible
                        searchResultsRecyclerView.visibility = View.VISIBLE
                        Log.d("Steam Search", "Search results list now has ${updatedList.size} items")
                    } else {
                        Log.d("Steam Search", "No Steam game results to display")
                    }
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("Steam Search", "Error searching Steam games: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                    // Only show Toast if specifically searching for Steam games
                    if (filterChipGroup.checkedChipId == R.id.chipSteam) {
                        Toast.makeText(context, "Error searching Steam games: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun searchUsers(query: String) {
        searchScope.launch(Dispatchers.IO) {
            try {
                val usersSnapshot = db.collection("users")
                    .whereGreaterThanOrEqualTo("username", query)
                    .whereLessThanOrEqualTo("username", query + "\uf8ff")
                    .limit(5)
                    .get()
                    .await()

                val userResults = usersSnapshot.documents.map { doc ->
                    SearchResult.User(
                        id = doc.id,
                        title = doc.getString("username") ?: "",
                        subtitle = doc.getString("bio") ?: "",
                        description = "",
                        imageUrl = doc.getString("avatarUrl") ?: "",
                        joinDate = doc.getTimestamp("joinDate")
                    )
                }

                withContext(Dispatchers.Main) {
                    searchResults.addAll(userResults)
                    searchResultsAdapter.submitList(searchResults.toList())
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error searching users", e)
                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    private fun searchLists(query: String) {
        searchScope.launch(Dispatchers.IO) {
            try {
                val listsSnapshot = db.collection("lists")
                    .whereGreaterThanOrEqualTo("name", query)
                    .whereLessThanOrEqualTo("name", query + "\uf8ff")
                    .limit(5)
                    .get()
                    .await()

                val listResults = listsSnapshot.documents.map { doc ->
                    SearchResult.List(
                        id = doc.id,
                        title = doc.getString("name") ?: "",
                        subtitle = doc.getString("description") ?: "",
                        description = "${doc.getLong("itemCount") ?: 0} items",
                        imageUrl = doc.getString("coverImageUrl") ?: "",
                        itemCount = doc.getLong("itemCount")?.toInt() ?: 0,
                        isPublic = doc.getBoolean("isPublic") ?: true,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }

                withContext(Dispatchers.Main) {
                    searchResults.addAll(listResults)
                    searchResultsAdapter.submitList(searchResults.toList())
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error searching lists", e)
                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    private fun searchGenres(query: String){
        searchScope.launch(Dispatchers.IO) {
            try {
                val lower_query = query.lowercase()
                val genresSnapshot = db.collection("games")
                    .whereArrayContains("genres", lower_query)
                    .limit(5)
                    .get()
                    .await()





                val genreResults = genresSnapshot.documents.map { doc ->
                    SearchResult.Game(
                        id = doc.id,
                        title = doc.getString("name") ?: "",
                        subtitle = doc.getString("publisher") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("coverImageUrl") ?: "",
                        rating = doc.getLong("rating")?.toInt() ?: 0,
                        releaseDate = doc.getTimestamp("releaseDate")
                    )
                }

                withContext(Dispatchers.Main) {
                    searchResults.addAll(genreResults)
                    searchResultsAdapter.submitList(searchResults.toList())
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error searching lists", e)

                withContext(Dispatchers.Main) {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }



    private fun handleSearchResultClick(result: SearchResult) {
        Log.d("SearchFragment", "Search result clicked: ${result.title}, type: ${result.type}")
        
        when (result) {
            is SearchResult.Game -> {
                Log.d("SearchFragment", "Regular game clicked: ${result.id}")
                // Create and show the game review fragment
                val gameReviewFragment = GameReviewFragment.newInstance(result.id)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, gameReviewFragment)
                    .addToBackStack(null)
                    .commit()
            }
            is SearchResult.User -> {
                val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),
                    MODE_PRIVATE
                )
                val userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"
                // TODO: Navigate to user profile
                if (result.id == userID){
                    loadFragment(AccountFragment())
                }
                else{
                    if(userID == "null"){
                        loadFragment(ReadOnlyProfileFragment(result.id, false))
                    }else{
                        db.collection("users").document(userID).get().addOnSuccessListener { user ->
                            //Make sharable data container
                            val sharableData = AccountContainer(userID, "User",user.get("username")as String)
                            //Check if following
                            db.collection("users").document(userID)
                                .collection("following").get().addOnSuccessListener { followings ->
                                    for (followed in followings){
                                        if(followed.id == result.id){
                                            loadFragment(ReadOnlyProfileFragment(result.id, true, sharableData))
                                            return@addOnSuccessListener
                                        }
                                    }
                                    loadFragment(ReadOnlyProfileFragment(result.id, false, sharableData))
                            }
                        }
                    }

//                    db.collection("users").document(userID)
//                        .collection("following").get().addOnSuccessListener { followings ->
//                            for (followed in followings) {
//                                if(followed.id == result.id){
//                                    loadFragment(ReadOnlyProfileFragment(result.id, true))
//                                    return@addOnSuccessListener
//                                }
//                            }
//                            loadFragment(ReadOnlyProfileFragment(result.id, false))
//                        }
                }
            }
            is SearchResult.List -> {
                Log.d("SearchFragment", "List clicked: ${result.id}")
                // Navigate to list details
                val listDetailsFragment = ListDetailsFragment.newInstance(result.id)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, listDetailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
            is SearchResult.SteamGame -> {
                Log.d("SearchFragment", "Steam game clicked: ${result.id}, title: ${result.title}")
                try {
                    // Handle Steam game click - open game details or review page
                    val gameReviewFragment = GameReviewFragment.newInstance(
                        gameId = result.id,
                        isSteamGame = true,
                        steamGameData = result
                    )
                    Log.d("SearchFragment", "Created GameReviewFragment for Steam game: ${result.title}")
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, gameReviewFragment)
                        .addToBackStack(null)
                        .commit()
                    Log.d("SearchFragment", "Transaction committed successfully")
                } catch (e: Exception) {
                    Log.e("SearchFragment", "Error handling Steam game click: ${e.message}", e)
                    Toast.makeText(context, "Error opening game details: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadRecentSearches() {
        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        val searches = prefs.getStringSet("recent_searches", setOf())?.toList() ?: emptyList()
        recentSearches.clear()
        recentSearches.addAll(searches)
        recentSearchAdapter.submitList(recentSearches)
    }

    private fun addRecentSearch(query: String) {
        if (query.isBlank()) return

        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        val searches = prefs.getStringSet("recent_searches", setOf())?.toMutableSet() ?: mutableSetOf()
        
        searches.remove(query)
        searches.add(query)
        
        if (searches.size > 5) {
            searches.remove(searches.first())
        }
        
        prefs.edit().putStringSet("recent_searches", searches).apply()
        
        recentSearches.clear()
        recentSearches.addAll(searches)
        recentSearchAdapter.submitList(recentSearches)
        
        // Track this search in popular searches
        incrementSearchCount(query)
    }

    private fun removeRecentSearch(query: String) {
        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        val searches = prefs.getStringSet("recent_searches", setOf())?.toMutableSet() ?: mutableSetOf()
        
        searches.remove(query)
        prefs.edit().putStringSet("recent_searches", searches).apply()
        
        recentSearches.remove(query)
        recentSearchAdapter.submitList(recentSearches)
    }

    private fun loadPopularSearches() {
        // Load popular searches from Firestore
        searchScope.launch(Dispatchers.IO) {
            try {
                // Get popular searches from Firestore - this queries the searches collection
                // and sorts by count (number of times the search was performed)
                val popularSearchesSnapshot = db.collection("popular_searches")
                    .orderBy("count", Query.Direction.DESCENDING)
                    .limit(5)
                    .get()
                    .await()

                val fetchedSearches = popularSearchesSnapshot.documents.mapNotNull { doc ->
                    doc.getString("query")
                }

                withContext(Dispatchers.Main) {
                    if (fetchedSearches.isNotEmpty()) {
                        // If we got results from Firestore, use them
                        popularSearches.clear()
                        popularSearches.addAll(fetchedSearches)
                        popularSearchAdapter.submitList(popularSearches)
                    } else {
                        // Fallback to sample searches if no results from Firestore
                        loadSamplePopularSearches()
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error loading popular searches: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Fallback to sample searches if there was an error
                    loadSamplePopularSearches()
                }
            }
        }
    }

    private fun loadSamplePopularSearches() {
        val sampleSearches = listOf(
            "Minecraft",
            "The Legend of Zelda",
            "Super Mario",
            "Pokemon",
            "Elden Ring"
        )
        popularSearches.clear()
        popularSearches.addAll(sampleSearches)
        popularSearchAdapter.submitList(popularSearches)
    }

    // Track popular searches by updating count in Firestore
    private fun incrementSearchCount(query: String) {
        if (query.isBlank()) return
        
        searchScope.launch(Dispatchers.IO) {
            try {
                // Update or create popular search document
                val searchDocRef = db.collection("popular_searches").document(query.lowercase())
                db.runTransaction { transaction ->
                    val searchDoc = transaction.get(searchDocRef)
                    val newCount = if (searchDoc.exists()) {
                        (searchDoc.getLong("count") ?: 0) + 1
                    } else {
                        1
                    }
                    
                    val searchData = hashMapOf(
                        "query" to query,
                        "count" to newCount,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    )
                    
                    transaction.set(searchDocRef, searchData)
                }
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error incrementing search count: ${e.message}", e)
            }
        }
    }

    private fun String.capitaliseEachWord(): String {
        val regex = "(\\b[a-z](?!\\s))".toRegex()
        return this.replace(regex) { it.value.uppercase() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchScope.cancel() // Cancel all coroutines when the view is destroyed
    }

    fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit{
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }
}

