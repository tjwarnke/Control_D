import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.ActivityAdapter
import com.example.controld.ActivityItem
import com.example.controld.R
import com.example.controld.data.model.ReviewModel
import com.example.controld.databinding.FragmentReviewListBinding
import com.example.controld.helper.dateToStringFullMonth
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.ktx.auth
import com.example.controld.CreateReviewFragment
import com.example.controld.ViewReviewFragment




class ReviewListFragment : Fragment() {
    private lateinit var gameID: String
    private lateinit var reviewsAdapter: ActivityAdapter
    private lateinit var reviewText: TextView
    private val firestore = Firebase.firestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        super.onCreate(savedInstanceState)
        gameID = arguments?.getString("gameId") ?: ""


        val binding = FragmentReviewListBinding.inflate(inflater, container, false)

        reviewsAdapter = ActivityAdapter(requireContext(), object : ActivityAdapter.Callbacks {
            override fun handleUserData(dataType: String, data: ActivityItem) {
                val currentUserID = getCurrentUserID() // You'll need to define this helper

                if (data.secondId == currentUserID) {
                    // It's the current user's review → go to CreateReviewFragment
                    loadFragment(CreateReviewFragment(data.id), true)
                } else {
                    // Someone else's review → go to ViewReviewFragment
                    loadFragment(ViewReviewFragment(data.id, data.secondId), true)
                }
            }
        })



        val recyclerView = binding.reviewsList
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = reviewsAdapter

        loadReviewsForGame()

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reviewText = view.findViewById(R.id.reviewText)
        var gameDatabase = firestore.collection("games").document(gameID)
        gameDatabase.get()
            .addOnSuccessListener { document ->
                val gamename = document.getString("name")
                reviewText.text = "Reviews for ${gamename}"
            }
    }

    private fun reviewModelToActivityItem(review: ReviewModel): ActivityItem {
        return ActivityItem(
            name = review.reviewTitle,
            gameTitle = "${review.rating}★ by ${review.username}",
            type = review.reviewBody.take(50), // preview of review
            avatarUrl = review.image,
            id = gameID,// Game ID
            secondId = review.userID
        )
    }

    private fun loadReviewsForGame() {
        if (gameID.isEmpty()) {
            Log.e("ReviewListFragment", "No gameID found in arguments")
            return
        }

        firestore.collection("games").document(gameID).collection("reviews")
            .get()
            .addOnSuccessListener { documents ->
                val activityItems = documents.mapNotNull { doc ->
                    try {
                        val dataMap = doc.data
                        Log.d("FirestoreDoc", "Raw doc data: $dataMap")

                        // Skip documents with unresolved FieldValue
                        if (dataMap.values.any { it is com.google.firebase.firestore.FieldValue }) {
                            Log.w("ReviewListFragment", "Skipping doc with unresolved FieldValue: ${doc.id}")
                            return@mapNotNull null
                        }

                        val review = doc.toObject(ReviewModel::class.java)
                        reviewModelToActivityItem(review)
                    } catch (e: Exception) {
                        Log.e("ReviewListFragment", "Error parsing document ${doc.id}", e)
                        null
                    }
                }

                reviewsAdapter.submitList(activityItems)
            }
            .addOnFailureListener { e ->
                Log.e("ReviewListFragment", "Error loading reviews", e)
            }
    }


    private fun getCurrentUserID(): String {
        val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),
            MODE_PRIVATE
        )
        var userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"
        return userID
    }

    private fun loadFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
    }

    companion object {
        fun newInstance(gameId: String): ReviewListFragment {
            val fragment = ReviewListFragment()
            val args = Bundle()
            args.putString("gameId", gameId)
            fragment.arguments = args
            return fragment
        }
    }
}
