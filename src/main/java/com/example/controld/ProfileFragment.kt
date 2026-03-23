package com.example.controld

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.StatsActivity
import com.example.controld.helper.dateToStringFullMonth
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout

class ProfileFragment : Fragment(), ActivityAdapter.Callbacks {
    private val firestore = Firebase.firestore
    private var userID = "null"
    private lateinit var sharableAccountData: AccountContainer
    private lateinit var profilePhoto: ShapeableImageView
    private lateinit var addPhotoButton: View
    private lateinit var usernameText: TextView
    private lateinit var joinDateText: TextView
    private lateinit var recentActivityList: RecyclerView
    private lateinit var gamesList: RecyclerView
    private lateinit var reviewsList: RecyclerView
    private lateinit var likesList: RecyclerView
    private lateinit var followingList: RecyclerView
    private lateinit var followersList: RecyclerView
    private lateinit var statsList: RecyclerView
    private lateinit var moreActivityButton: View
    private lateinit var logoutButton: View
    private lateinit var myGamesButton: MaterialButton
    private lateinit var myReviewsButton: MaterialButton
    private lateinit var myLikedGamesButton: MaterialButton
    private lateinit var followingButton: MaterialButton
    private lateinit var myFollowersButton: MaterialButton
    private lateinit var statsButton: MaterialButton
    private lateinit var steamConnectButton: MaterialButton
    private lateinit var originalSteamButtonParent: ViewGroup
    private lateinit var originalSteamButtonParams: ViewGroup.LayoutParams
    private var originalSteamButtonIndex: Int = -1

    private lateinit var activityAdapter: ActivityAdapter
    private lateinit var gamesAdapter: ActivityAdapter
    private lateinit var reviewsAdapter: ActivityAdapter
    private lateinit var likesAdapter: ActivityAdapter
    private lateinit var followingAdapter: ActivityAdapter
    private lateinit var followersAdapter: ActivityAdapter
    private lateinit var statsAdapter: ActivityAdapter
    private lateinit var moreActivityAdapter: ActivityAdapter

    private var isSteamConnected = false
    private var steamId: String? = null
    private var currentSortType = SortType.NEWEST
    private val TAG = "ProfileFragment"

    private val steamAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val steamId = result.data?.getStringExtra("steam_id")
            val steamName = result.data?.getStringExtra("steam_name")
            val steamAvatar = result.data?.getStringExtra("steam_avatar")
            
            if (steamId != null) {
                saveSteamIdToProfile(steamId, steamName ?: "", steamAvatar ?: "")
            } else {
                Toast.makeText(context, getString(R.string.steam_connection_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, getString(R.string.steam_connection_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                profilePhoto.setImageURI(uri)
                Toast.makeText(context, getString(R.string.profile_photo_updated), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activityAdapter = ActivityAdapter(context, this)
        gamesAdapter = ActivityAdapter(context, this)
        reviewsAdapter = ActivityAdapter(context, this)
        likesAdapter = ActivityAdapter(context, this)
        followingAdapter = ActivityAdapter(context, this)
        followersAdapter = ActivityAdapter(context, this)
        statsAdapter = ActivityAdapter(context, this)
        moreActivityAdapter = ActivityAdapter(context, this)

        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //get userID
        val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
        userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"

        // Initialize views
        profilePhoto = view.findViewById(R.id.profile_photo)
        addPhotoButton = view.findViewById(R.id.add_photo_button)
        usernameText = view.findViewById(R.id.username)
        joinDateText = view.findViewById(R.id.join_date)
        recentActivityList = view.findViewById(R.id.recent_activity_list)
        gamesList = view.findViewById(R.id.games_list)
        reviewsList = view.findViewById(R.id.reviews_list)
        likesList = view.findViewById(R.id.likes_list)
        followingList = view.findViewById(R.id.following_list)
        followersList = view.findViewById(R.id.followers_list)
        statsList = view.findViewById(R.id.stats_list)
        moreActivityButton = view.findViewById(R.id.more_activity_button)
        logoutButton = view.findViewById(R.id.logout)
        myGamesButton = view.findViewById(R.id.my_games_button)
        myReviewsButton = view.findViewById(R.id.my_reviews_button)
        myLikedGamesButton = view.findViewById(R.id.my_liked_games_button)
        followingButton = view.findViewById(R.id.following_button)
        myFollowersButton = view.findViewById(R.id.my_followers_button)
        statsButton = view.findViewById(R.id.stats_button)
        steamConnectButton = view.findViewById(R.id.steam_connect_button)
        
        // Store original button parent and position for later restoration
        originalSteamButtonParent = steamConnectButton.parent as ViewGroup
        originalSteamButtonParams = steamConnectButton.layoutParams
        originalSteamButtonIndex = originalSteamButtonParent.indexOfChild(steamConnectButton)

        val TAG = "UserDB"
        val userRef = firestore.collection("users").document(userID)
        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    Log.d(TAG, "${document.data}")
                    usernameText.text = document.getString("username")
                    //todo add joinDateText to be updated from the dataset
                    //requires a helper code to put the date in the right format rather than timestamp
                    //Add to sharableAccountContainer
                    sharableAccountData = AccountContainer(
                        document.id,
                        "User",//Placeholder
                        document.get("username").toString()
                    )
                    
                    // Check if user has a Steam account connected
                    steamId = document.getString("steam_id")
                    if (!steamId.isNullOrEmpty()) {
                        isSteamConnected = true
                        steamConnectButton.text = getString(R.string.steam_connected)
                    }
                    
                    // Load appropriate games content based on Steam connection status
                    loadGamesContent()
                }
                else{
                    Log.d(TAG, "not found")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with", exception)
            }


        // Set up the join date
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(System.currentTimeMillis())
        joinDateText.text = getString(R.string.profile_join_date_format, currentDate)

        // Set up photo button
        addPhotoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        
        // Set up Steam connect button
        steamConnectButton.setOnClickListener {
            startSteamAuth()
        }

        // Set up RecyclerViews
        setupRecyclerViews()

        // Set up more activity button
        moreActivityButton.setOnClickListener {
            showMoreActivityDialog()
        }

        // Set up button click listeners
        myGamesButton.setOnClickListener {
            startActivity(GamesActivity.createIntent(requireContext()))
        }

        myReviewsButton.setOnClickListener {
            startActivity(ReviewsActivity.createIntent(requireContext(), true))
        }

        myLikedGamesButton.setOnClickListener {
            startActivity(Intent(requireContext(), LikesActivity::class.java))
        }

        followingButton.setOnClickListener {
            startActivity(Intent(requireContext(), FollowingActivity::class.java))
        }

        myFollowersButton.setOnClickListener {
            startActivity(Intent(requireContext(), FollowersActivity::class.java))
        }

        statsButton.setOnClickListener {
            startActivity(Intent(requireContext(), StatsActivity::class.java))
        }

        // Set up section title clicks
        view.findViewById<TextView>(R.id.games_title).setOnClickListener {
            startActivity(Intent(requireContext(), GamesActivity::class.java))
        }

        view.findViewById<TextView>(R.id.reviews_title).setOnClickListener {
            startActivity(Intent(requireContext(), ReviewsActivity::class.java))
        }

        view.findViewById<TextView>(R.id.likes_title).setOnClickListener {
            startActivity(Intent(requireContext(), LikesActivity::class.java))
        }

        view.findViewById<TextView>(R.id.following_title).setOnClickListener {
            startActivity(Intent(requireContext(), FollowingActivity::class.java))
        }

        view.findViewById<TextView>(R.id.followers_title).setOnClickListener {
            startActivity(Intent(requireContext(), FollowersActivity::class.java))
        }

        view.findViewById<TextView>(R.id.stats_title).setOnClickListener {
            startActivity(Intent(requireContext(), StatsActivity::class.java))
        }

        logoutButton.setOnClickListener {
            sharedPref?.edit()
                ?.putString(getString(R.string.emailKey), "null")
                ?.putString(getString(R.string.passwordKey),"null")
                ?.apply()
            Snackbar.make(view, "Successfully logged out", Snackbar.LENGTH_SHORT).show()
            loadFragment(SignInFragment(), false)
        }
    }
    
    private fun startSteamAuth() {
        val intent = Intent(requireContext(), SteamAuthActivity::class.java)
        steamAuthLauncher.launch(intent)
    }
    
    private fun saveSteamIdToProfile(steamId: String, steamName: String, steamAvatar: String) {
        if (userID == "null") return
        
        val userRef = firestore.collection("users").document(userID)
        userRef.update(
            mapOf(
                "steam_id" to steamId,
                "steam_name" to steamName,
                "steam_avatar" to steamAvatar
            )
        ).addOnSuccessListener {
            Log.d(TAG, "Steam account connected: $steamId ($steamName)")
            Toast.makeText(context, getString(R.string.steam_connected), Toast.LENGTH_SHORT).show()
            steamConnectButton.text = getString(R.string.steam_connected)
            this.steamId = steamId
            isSteamConnected = true
            
            // Reload games content after connecting Steam
            loadGamesContent()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error saving Steam ID to profile", e)
            Toast.makeText(context, getString(R.string.steam_connection_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerViews() {
        // Setup Recent Activity RecyclerView
        recentActivityList.layoutManager = LinearLayoutManager(context)
        recentActivityList.adapter = activityAdapter

        // Setup Games RecyclerView
        gamesList.layoutManager = LinearLayoutManager(context)
        gamesList.adapter = gamesAdapter

        // Setup Reviews RecyclerView
        reviewsList.layoutManager = LinearLayoutManager(context)
        reviewsList.adapter = reviewsAdapter

        // Setup Likes RecyclerView
        likesList.layoutManager = LinearLayoutManager(context)
        likesList.adapter = likesAdapter

        // Setup Following RecyclerView
        followingList.layoutManager = LinearLayoutManager(context)
        followingList.adapter = followingAdapter

        // Setup Followers RecyclerView
        followersList.layoutManager = LinearLayoutManager(context)
        followersList.adapter = followersAdapter

        // Setup Stats RecyclerView
        statsList.layoutManager = LinearLayoutManager(context)
        statsList.adapter = statsAdapter

        CoroutineScope(Main).launch{
            val sampleGames = populateGames()
            val sampleReviews = populateReviews()
            val sampleFollowing = populateFollowing()
            val sampleLikes = populateLikes()
            val sampleFollowers = populateFollowers()
            
            // Don't load games here anymore, it's handled by loadGamesContent() based on Steam connection

            gamesAdapter.submitList(sampleGames)
            reviewsAdapter.submitList(sampleReviews)
            followingAdapter.submitList(sampleFollowing)
            likesAdapter.submitList(sampleLikes)
            followersAdapter.submitList(sampleFollowers)
        }

        // Sample data for stats
        val sampleStats = listOf(
            ActivityItem(
                name = "Games Completed",
                gameTitle = "42",
                type = "Total",
                avatarUrl = "https://example.com/stats.jpg"
            ),
            ActivityItem(
                name = "Reviews Written",
                gameTitle = "28",
                type = "Total",
                avatarUrl = "https://example.com/stats.jpg"
            ),
            ActivityItem(
                name = "Hours Played",
                gameTitle = "1,234",
                type = "Total",
                avatarUrl = "https://example.com/stats.jpg"
            )
        )
        statsAdapter.submitList(sampleStats)
    }

    private fun showMoreActivityDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_more_activity, null)
        val activityRecyclerView = dialogView.findViewById<RecyclerView>(R.id.activity_recycler_view)
        val dateChipGroup = dialogView.findViewById<ChipGroup>(R.id.date_sort_chips)

        // Setup RecyclerView
        activityRecyclerView.layoutManager = LinearLayoutManager(context)
        activityRecyclerView.adapter = moreActivityAdapter

        // Set initial sort selection
        when (currentSortType) {
            SortType.NEWEST -> dateChipGroup.check(R.id.sort_newest)
            SortType.OLDEST -> dateChipGroup.check(R.id.sort_oldest)
            else -> dateChipGroup.check(R.id.sort_newest)
        }

        // Load all activities
        loadAllActivities()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loadAllActivities() {
        // Combine all activities from different sections
        val allActivities = mutableListOf<ActivityItem>()

        // Add activities from each section
        activityAdapter.currentList.forEach { allActivities.add(it) }
        gamesAdapter.currentList.forEach { allActivities.add(it) }
        reviewsAdapter.currentList.forEach { allActivities.add(it) }
        likesAdapter.currentList.forEach { allActivities.add(it) }
        followingAdapter.currentList.forEach { allActivities.add(it) }
        followersAdapter.currentList.forEach { allActivities.add(it) }

        allActivities.forEach { item ->

        }

        // Sort activities based on current sort type
        val sortedActivities = when (currentSortType) {
            SortType.NEWEST -> allActivities.sortedByDescending { it.date }
            SortType.OLDEST -> allActivities.sortedBy { it.date }
            SortType.NAME_ASC -> allActivities.sortedBy { it.gameTitle }
            SortType.NAME_DESC -> allActivities.sortedByDescending { it.gameTitle }
        }

        moreActivityAdapter.submitList(sortedActivities)
    }

    // getting stuff from the dataset
    private suspend fun populateReviews(): MutableList<ActivityItem> {
        val TAG = "MYLOGTAG"
        val test1 = "https://example.com/elden-ring.jpg"
        val sampleReviews = CoroutineScope(IO).async {
            val reviewlist = mutableListOf<ActivityItem>()
            try{

                val query = firestore.collection("users").document(userID)
                    .collection("reviews").get().await()
                Log.d("ReviewCheck", "{$query}")
                for (game in query){
                    Log.d("ReviewCheck", "{$game}")
                    if (game.data["reviewDate"] != null){
                        val gameid = game.data["gameID"] as String
                        val gamename = game.data["gamename"] as String
                        val rating = game.data["rating"] as Double
                        val cleanRating = handleNumber(rating)
                        val stringRating = "$cleanRating / 5"
                        val reviewdate = game.data["reviewDate"] as Timestamp
                        val stringreviewdate = dateToStringFullMonth(reviewdate)
                        val image = game.data["image"] as String
                        reviewlist.add(ActivityItem(gamename, stringRating, stringreviewdate.toString(), image, gameid))
                    }
                }
            }
            catch(e: Exception){
                Log.d(TAG, "Error populating games or reviews: ${e}")
            }
            Log.d(TAG, "${reviewlist}")
            return@async reviewlist
        }
        return sampleReviews.await()
    }

    private suspend fun populateFollowing(): MutableList<ActivityItem> {
        val TAG = "MYLOGTAG"
        val test1 = "https://example.com/elden-ring.jpg"
        val sampleFollowing = CoroutineScope(IO).async {
            val followinglist = mutableListOf<ActivityItem>()
            try{
                val query = firestore.collection("users").document(userID)
                    .collection("following").get().await()
                for (user in query) {
                    val userid = user.data["userID"] as String
                    val username = user.data["username"] as String
                    val dateadded = user.data["dateAdded"] as Timestamp
                    val stringdate = dateToStringFullMonth(dateadded)
                    followinglist.add(ActivityItem(username,"Following", "", "", userid))
                }
            }
            catch(e: Exception){
                Log.d(TAG, "Error populating following: ${e}")
            }
            Log.d(TAG, "${followinglist}")
            return@async followinglist
        }
        return sampleFollowing.await()
    }

    private suspend fun populateLikes(): MutableList<ActivityItem> {
        val TAG = "MYLOGTAG"
        val test1 = "https://example.com/elden-ring.jpg"
        val sampleLikes = CoroutineScope(IO).async {
            val likeslist = mutableListOf<ActivityItem>()
            try{
                val query = firestore.collection("users").document(userID)
                    .collection("likes").get().await()
                for (like in query) {
                    val gamename = like.data["gamename"] as String
                    val dateadded = like.data["reviewDate"] as Timestamp
                    val stringdate = dateToStringFullMonth(dateadded)
                    val gameId = like.data["gameID"] as String
                    val viewuserID = like.data["userID"] as String
                    val image = like.data["image"] as String
                    val username = like.data["username"] as String
                    likeslist.add(ActivityItem(gamename, "Liked ${username}'s Review",
                        stringdate.toString(), image, gameId, viewuserID))
                }
            }
            catch(e: Exception){
                Log.d(TAG, "Error populating likes: ${e}")
            }
            Log.d(TAG, "${likeslist}")
            return@async likeslist
        }
        return sampleLikes.await()
    }

    private suspend fun populateFollowers(): MutableList<ActivityItem> {
        val TAG = "MYLOGTAG"
        val test1 = "https://example.com/elden-ring.jpg"
        val sampleFollowers = CoroutineScope(IO).async {
            val followerlist = mutableListOf<ActivityItem>()
            try{
                val query = firestore.collection("users").document(userID)
                    .collection("followers").get().await()
                for (user in query) {
                    val userid = user.data["userID"] as String
                    val username = user.data["username"] as String
                    val dateadded = user.data["dateAdded"] as Timestamp
                    val stringdate = dateToStringFullMonth(dateadded)
                    followerlist.add(ActivityItem(username, "Follower", "", test1, userid))
                }
            }
            catch(e: Exception){
                Log.d(TAG, "Error populating following: ${e}")
            }
            Log.d(TAG, "${followerlist}")
            return@async followerlist
        }
        return sampleFollowers.await()
    }

    private fun loadFragment(fragment: Fragment, canGoBack: Boolean = true) {
        parentFragment?.parentFragmentManager?.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
            if (canGoBack) addToBackStack(null)
        }
    }

    private fun handleNumber(number: Double): Number {
        return if (number.mod(1.0) == 0.0) {
            number.toInt()
        }
        else {
            number
        }

    }

    override fun handleUserData(dataType: String, data: ActivityItem) {
        Log.d("Crash", dataType)
        if(dataType == "Following"){
            loadFragment(ReadOnlyProfileFragment(data.id, true, sharableAccountData))
        }
        if(dataType == "Follower"){
            //Check if user is following them
            firestore.collection("users").document(userID)
                .collection("following").get().addOnSuccessListener { followings ->
                    for (followed in followings) {
                        if(followed.id == data.id){
                            loadFragment(ReadOnlyProfileFragment(data.id, true, sharableAccountData))
                            return@addOnSuccessListener
                        }
                    }
                    loadFragment(ReadOnlyProfileFragment(data.id, false, sharableAccountData))
                }
        }
        if(dataType == "Favorited"){
            loadFragment(GameReviewFragment.newInstance(data.id))
        }
        if(dataType == "List"){
            loadFragment(ListsFragment.newInstance(showPublicLists = false, listId = data.id))
        }
        if (dataType.contains("/ 5")){
            loadFragment(CreateReviewFragment(data.id))
        }
        //User cannot like own review so always visiting another persons review (Gameid, then userID)
        if(dataType.contains("Review")){
            loadFragment(ViewReviewFragment(data.id, data.secondId))
        }
        if (dataType.contains("/ 5")){
            loadFragment(CreateReviewFragment(data.id))
        }
    }


    private suspend fun populateGames(): MutableList<ActivityItem> {
        val TAG = "MYLOGTAG"
        val test1 = "https://example.com/elden-ring.jpg"
        val sampleGames = CoroutineScope(IO).async {
            val gamelist = mutableListOf<ActivityItem>()
            try{
                val query = firestore.collection("users").document(userID)
                    .collection("favorites").get().await()
                Log.d("GameCheck", "{$query}")
                for (game in query){
                    Log.d("GameCheck", "{$game}")
                    if (game.data["gameID"] != null) {
                        val gameid = game.data["gameID"] as String
                        val gamename = game.data["gamename"] as String
                        val dateadded = game.data["dateAdded"] as Timestamp
                        val image = game.data["image"] as String
                        val stringdate = dateToStringFullMonth(dateadded)
                        gamelist.add(ActivityItem(gamename,"Favorited", stringdate.toString(), image, gameid))
                    }
                }
            }
            catch(e: Exception){
                Log.d(TAG, "Error populating games or reviews: ${e}")
            }
            Log.d(TAG, "${gamelist}")
            return@async gamelist
        }
        return sampleGames.await()
    }

    /**
     * Load appropriate content for the games section based on Steam connection status
     */
    private fun loadGamesContent() {
        return
        val gamesContainer = view?.findViewById<ViewGroup>(R.id.games_container) ?: return

        if (isSteamConnected && !steamId.isNullOrEmpty()) {
            // User has connected Steam, load their games
            CoroutineScope(Main).launch {
                val steamGames = loadSteamGames()
                
                // Remove any existing message or button if it was added
                val existingMessage = gamesContainer.findViewById<TextView>(R.id.connect_steam_message)
                if (existingMessage != null) {
                    gamesContainer.removeView(existingMessage)
                }
                
                // Make sure Steam button is in its original position
                if (steamConnectButton.parent == gamesContainer) {
                    gamesContainer.removeView(steamConnectButton)
                    
                    // Restore original button position
                    if (originalSteamButtonIndex >= 0 && originalSteamButtonIndex < originalSteamButtonParent.childCount) {
                        originalSteamButtonParent.addView(steamConnectButton, originalSteamButtonIndex, originalSteamButtonParams)
                    } else {
                        originalSteamButtonParent.addView(steamConnectButton, originalSteamButtonParams)
                    }
                }
                
                // Make sure the games list is visible
                gamesList.visibility = View.VISIBLE
                myGamesButton.visibility = View.VISIBLE
                
                // Show the games
                gamesAdapter.submitList(steamGames)
            }
        } else {
            // User hasn't connected Steam, show message and connect button
            
            // Clear any existing games
            gamesAdapter.submitList(emptyList())
            
            // Hide the games list and button
            gamesList.visibility = View.GONE
            myGamesButton.visibility = View.GONE
            
            // Check if we already have a message view
            var connectSteamMessageView = gamesContainer.findViewById<TextView>(R.id.connect_steam_message)
            
            if (connectSteamMessageView == null) {
                // Create a TextView with the message
                connectSteamMessageView = TextView(context).apply {
                    id = R.id.connect_steam_message
                    text = "Please connect Steam to show games"
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 20, 0, 20)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                
                // Add the message view to the games container
                gamesContainer.addView(connectSteamMessageView, 0)
                
                // Move Steam connect button to the games container
                val steamButtonParent = steamConnectButton.parent as? ViewGroup
                steamButtonParent?.removeView(steamConnectButton)
                
                // Add layout parameters for the button to center it
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.gravity = android.view.Gravity.CENTER
                layoutParams.setMargins(0, 0, 0, 20)
                steamConnectButton.layoutParams = layoutParams
                
                // Add the button below the message
                gamesContainer.addView(steamConnectButton)
            }
        }
    }
    
    /**
     * Load games from Steam for the connected account
     */
    private suspend fun loadSteamGames(): List<ActivityItem> {
        if (!isSteamConnected || steamId.isNullOrEmpty()) {
            return emptyList()
        }
        
        return CoroutineScope(IO).async {
            val gamesList = mutableListOf<ActivityItem>()
            try {
                // Create SteamApi and SteamGameManager instances
                val steamApi = com.example.controld.steam.SteamApiClient.create()
                val steamGameManager = com.example.controld.steam.SteamGameManager(steamApi)
                
                // Load owned games
                val ownedGames = steamGameManager.getOwnedGames(
                    apiKey = BuildConfig.STEAM_API_KEY,
                    steamId = steamId ?: ""
                )
                
                // Convert to ActivityItem objects
                ownedGames.forEach { game ->
                    val playtime = game.playtime_forever / 60 // Convert minutes to hours
                    gamesList.add(
                        ActivityItem(
                            name = game.name,
                            gameTitle = "Steam Game",
                            type = if (playtime > 0) "${playtime}h played" else "Not played yet",
                            avatarUrl = "https://steamcdn-a.akamaihd.net/steam/apps/${game.appid}/header.jpg",
                            id = game.appid.toString()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Steam games", e)
            }
            
            return@async gamesList
        }.await()
    }

}