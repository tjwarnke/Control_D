package com.example.controld

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.controld.data.model.ReviewModel
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewsActivity : AbstractListActivity() {
    private val firestore = Firebase.firestore
    private var showOnlyUserReviews = false
    private val TAG = "ReviewsActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Get the intent extra to determine mode
        showOnlyUserReviews = intent.getBooleanExtra("SHOW_USER_REVIEWS_ONLY", false)
        super.onCreate(savedInstanceState)
    }
    
    override fun getTitleString(): String {
        return if (showOnlyUserReviews) {
            getString(R.string.my_reviews)
        } else {
            getString(R.string.all_reviews)
        }
    }

    override fun loadData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val reviews = if (showOnlyUserReviews) {
                    // Get only the current user's reviews
                    loadUserReviews()
                } else {
                    // Get all reviews across all games
                    loadAllReviews()
                }
                
                // Sort the data based on the current sort type
                val sortedReviews = when (currentSortType) {
                    SortType.NEWEST -> reviews.sortedByDescending { it.date }
                    SortType.OLDEST -> reviews.sortedBy { it.date }
                    SortType.NAME_ASC -> reviews.sortedBy { it.gameTitle }
                    SortType.NAME_DESC -> reviews.sortedByDescending { it.gameTitle }
                    else -> reviews
                }
                
                adapter.submitList(sortedReviews)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reviews", e)
            }
        }
    }
    
    private suspend fun loadUserReviews(): List<ActivityItem> {
        val userID = getCurrentUserID()
        if (userID == "null") return emptyList()
        
        val reviewList = mutableListOf<ActivityItem>()
        try {
            val query = firestore.collection("users").document(userID)
                .collection("reviews").get().await()
                
            for (doc in query) {
                if (doc.data["reviewDate"] != null) {
                    val gameid = doc.data["gameID"] as String
                    val gamename = doc.data["gamename"] as String
                    val rating = doc.data["rating"] as Double
                    val cleanRating = handleNumber(rating)
                    val stringRating = "$cleanRating / 5"
                    val reviewdate = doc.data["reviewDate"] as Timestamp
                    val stringreviewdate = dateToStringFullMonth(reviewdate.toDate())
                    val image = doc.data["image"] as String
                    
                    reviewList.add(ActivityItem(
                        name = gamename, 
                        gameTitle = stringRating, 
                        type = stringreviewdate.toString(), 
                        avatarUrl = image, 
                        id = gameid,
                        secondId = userID,  // secondId stores the userID
                        date = reviewdate.toDate()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user reviews: ${e}")
        }
        
        return reviewList
    }
    
    private suspend fun loadAllReviews(): List<ActivityItem> {
        val reviewList = mutableListOf<ActivityItem>()
        
        try {
            // First get all games
            val gamesQuery = firestore.collection("games").get().await()
            
            // For each game, get its reviews
            for (gameDoc in gamesQuery) {
                val gameId = gameDoc.id
                val gameName = gameDoc.getString("name") ?: "Unknown Game"
                val gameImage = gameDoc.getString("image") ?: ""
                
                val reviewsQuery = firestore.collection("games").document(gameId)
                    .collection("reviews").get().await()
                    
                for (reviewDoc in reviewsQuery) {
                    try {
                        val review = reviewDoc.toObject(ReviewModel::class.java)
                        val rating = review.rating
                        val cleanRating = handleNumber(rating)
                        val stringRating = "$cleanRating / 5"
                        val stringDate = dateToStringFullMonth(review.reviewDate)
                        
                        reviewList.add(ActivityItem(
                            name = gameName,
                            gameTitle = stringRating,
                            type = stringDate,
                            avatarUrl = gameImage,
                            id = gameId, 
                            secondId = review.userID,  // This is the user who wrote the review
                            date = review.reviewDate ?: Date()
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing review: ${e}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all reviews: ${e}")
        }
        
        return reviewList
    }
    
    private fun getCurrentUserID(): String {
        val sharedPref = getSharedPreferences(getString(R.string.accountPrefKey), Context.MODE_PRIVATE)
        return sharedPref.getString(getString(R.string.emailKey), "null") ?: "null"
    }
    
    private fun handleNumber(number: Double): Number {
        return if (number.mod(1.0) == 0.0) {
            number.toInt()
        } else {
            number
        }
    }
    
    private fun dateToStringFullMonth(date: Date?): String {
        if (date == null) return ""
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        return dateFormat.format(date)
    }
    
    companion object {
        fun createIntent(context: Context, showUserReviewsOnly: Boolean): Intent {
            val intent = Intent(context, ReviewsActivity::class.java)
            intent.putExtra("SHOW_USER_REVIEWS_ONLY", showUserReviewsOnly)
            return intent
        }
    }
}