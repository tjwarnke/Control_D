package com.example.controld

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.StatsActivity
import com.example.controld.helper.dateToStringFullMonth
import com.example.controld.userDatabase.generateHash
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
import java.util.Date
import com.google.android.material.button.MaterialButton

class ReadOnlyProfileFragment(private val userID: String, private var isFollowing: Boolean, private val ogSharableData: AccountContainer? = null) : Fragment(), ActivityAdapter.Callbacks {
    private val firestore = Firebase.firestore
    private lateinit var ogUser: String
    private lateinit var sharableAccountData: AccountContainer
    private lateinit var profilePhoto: ShapeableImageView
    private lateinit var followButton: Button
    private lateinit var usernameText: TextView
    private lateinit var joinDateText: TextView
    private lateinit var recentActivityList: RecyclerView
    private lateinit var gamesList: RecyclerView
    private lateinit var reviewsList: RecyclerView
    private lateinit var likesList: RecyclerView
    private lateinit var followingList: RecyclerView
    private lateinit var followersList: RecyclerView
    private lateinit var statsList: RecyclerView

    private lateinit var activityAdapter: ActivityAdapter
    private lateinit var gamesAdapter: ActivityAdapter
    private lateinit var reviewsAdapter: ActivityAdapter
    private lateinit var likesAdapter: ActivityAdapter
    private lateinit var followingAdapter: ActivityAdapter
    private lateinit var followersAdapter: ActivityAdapter
    private lateinit var statsAdapter: ActivityAdapter
    private lateinit var moreActivityAdapter: ActivityAdapter

    private var currentSortType = SortType.NEWEST


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

        return inflater.inflate(R.layout.fragment_profile_guest, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Initialize current user
        ogUser = requireContext().getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
            .getString(getString(R.string.emailKey),"null") ?: "null"

        if(userID == ogUser){
            loadFragment(AccountFragment(),false)
        }

        // Initialize views
        profilePhoto = view.findViewById(R.id.profile_photo)
        followButton = view.findViewById(R.id.follow)
        usernameText = view.findViewById(R.id.username)
        joinDateText = view.findViewById(R.id.join_date)
        recentActivityList = view.findViewById(R.id.recent_activity_list)
        gamesList = view.findViewById(R.id.games_list)
        reviewsList = view.findViewById(R.id.reviews_list)
        likesList = view.findViewById(R.id.likes_list)
        followingList = view.findViewById(R.id.following_list)
        followersList = view.findViewById(R.id.followers_list)
        statsList = view.findViewById(R.id.stats_list)

        //Set follow button text
        followButton.text = if (isFollowing) getString(R.string.profile_following) else getString(R.string.profile_follow)

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

        // Set up follow button
        followButton.setOnClickListener {
            if(ogUser == "null"){
                loadFragment(SignInFragment())
                return@setOnClickListener
            }
            if(isFollowing){
                isFollowing = false
                followButton.text = getString(R.string.profile_follow)

                //Update firebase
                firestore.collection("users").document(ogUser).collection("following")
                    .document(userID).delete()
                    .addOnSuccessListener { Log.d("tag","Account unfollowed.") }
                firestore.collection("users").document(userID).collection("followers")
                    .document(ogUser).delete()
                    .addOnSuccessListener { Log.d("tag","OG user follower removed.")
                        Snackbar.make(view, "User unfollowed", Snackbar.LENGTH_SHORT).show()}

            }else{
                isFollowing = true
                followButton.text = getString(R.string.profile_following)
                //Change database
                sharableAccountData.updateDate()
                firestore.collection("users").document(ogUser).collection("following")
                    .document(userID).set(sharableAccountData)
                    .addOnSuccessListener { Log.d("tag","Account followed.") }
                if(ogSharableData != null){
                    ogSharableData.updateDate()
                    firestore.collection("users").document(userID).collection("followers")
                        .document(ogUser).set(ogSharableData)
                        .addOnSuccessListener { Log.d("tag","OG follower added.")
                            Snackbar.make(view, "User followed", Snackbar.LENGTH_SHORT).show()}
                }
            }
        }

        // Set up RecyclerViews
        setupRecyclerViews()

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

        // Sample data for recent activities
        val sampleActivities = listOf(
            ActivityItem(
                "The Legend of Zelda: Breath of the Wild",
                "Started playing",
                "March 15, 2024",
                "https://example.com/botw.jpg"
            ),
            ActivityItem(
                "Red Dead Redemption 2",
                "Completed",
                "March 10, 2024",
                "https://example.com/rdr2.jpg"
            ),
            ActivityItem(
                "God of War",
                "Added to wishlist",
                "March 5, 2024",
                "https://example.com/gow.jpg"
            )
        )
        activityAdapter.submitList(sampleActivities)

        CoroutineScope(Main).launch{
            val sampleFollowing = populateFollowing()
            val sampleLikes = populateLikes()
            val sampleFollowers = populateFollowers()
            val sampleGames = populateGames()
            val sampleReviews = populateReviews()
            gamesAdapter.submitList(sampleGames)
            reviewsAdapter.submitList(sampleReviews)
            followingAdapter.submitList(sampleFollowing)
            likesAdapter.submitList(sampleLikes)
            followersAdapter.submitList(sampleFollowers)
        }


        // Sample data for stats
        val sampleStats = listOf(
            ActivityItem(
                "Games Completed",
                "42",
                "Total",
                "https://example.com/stats.jpg"
            ),
            ActivityItem(
                "Reviews Written",
                "28",
                "Total",
                "https://example.com/stats.jpg"
            ),
            ActivityItem(
                "Hours Played",
                "1,234",
                "Total",
                "https://example.com/stats.jpg"
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
                        val userid = game.data["userID"] as String
                        val cleanRating = handleNumber(rating)
                        val stringRating = "$cleanRating / 5"
                        val reviewdate = game.data["reviewDate"] as Timestamp
                        val stringreviewdate = dateToStringFullMonth(reviewdate)
                        val image = game.data["image"] as String
                        reviewlist.add(ActivityItem(gamename, stringRating, stringreviewdate.toString(), image, gameid, userid))
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
                    followinglist.add(ActivityItem(username, "Following", "", test1, userid))
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
                    val usertitle = user.data["userTitle"] as String
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

    fun loadFragment(fragment: Fragment, canGoBack: Boolean = true) {
        parentFragmentManager.commit{
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
        Log.d("ROProfile",dataType)
        Log.d("ROProfile",data.toString())
        if(dataType == "Following" || dataType == "Follower"){
            Log.d("ROProfileFragment","clicked on following account")
            //accessing their own account again
            if(ogUser == data.id){
                loadFragment(AccountFragment())
                return
            }
            //Can't be a follower if you don't have an account
            if(ogUser == "null"){
                loadFragment(ReadOnlyProfileFragment(data.id, false, ogSharableData))
                return
            }
            //Check if this user is in user's following list
            firestore.collection("users").document(ogUser)
                .collection("following").get().addOnSuccessListener { followings ->
                    for (followed in followings) {
                        if(followed.id == data.id){
                            loadFragment(ReadOnlyProfileFragment(data.id, true, ogSharableData))
                            return@addOnSuccessListener
                        }
                    }
                    loadFragment(ReadOnlyProfileFragment(data.id, false, ogSharableData))
                }
        }
        if(dataType.contains("/ 5") || dataType.contains("Review")){
            Log.d("ROProfileFragment", "clicking on review/liked review")
            //Case for clicking a liked review that the viewer made
            if(ogUser == data.secondId){
                loadFragment(CreateReviewFragment(data.id))

            }
            else{
                loadFragment(ViewReviewFragment(data.id, data.secondId))
            }

        }
        if(dataType == "Favorited"){
            loadFragment(GameReviewFragment.newInstance(data.id))
        }

    }

}