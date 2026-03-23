package com.example.controld

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import androidx.fragment.app.Fragment
import android.widget.RatingBar
import android.widget.TextView
import androidx.fragment.app.commit
import com.bumptech.glide.Glide
import com.example.controld.data.model.ReviewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions



class CreateReviewFragment(private var gameID: String) : Fragment() {

    private lateinit var imageView : ImageView
    private lateinit var ratingBar: RatingBar
    private lateinit var reviewBody: TextView
    private lateinit var reviewTitle: TextView
    private lateinit var submitButton: Button
    private lateinit var deleteButton: Button
    private var userRating: Double = 0.0
    private lateinit var username: String

    companion object {
        private const val ARG_GAME_ID = "game_id"

        fun newInstance(gameId: String): CreateReviewFragment {
            val gameID = ""
            val fragment = CreateReviewFragment(gameID)
            val args = Bundle()
            args.putString(ARG_GAME_ID, gameId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            gameID = it.getString(ARG_GAME_ID, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //view homepage variable
        var v = inflater.inflate(R.layout.fragment_create_review, container, false)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),
            MODE_PRIVATE
        )
        val userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"

        val firestore = Firebase.firestore

        imageView = view.findViewById(R.id.imageView)

        // Find RatingBar from layout
        ratingBar = view.findViewById(R.id.ratingBar)

        //Find BodyText and Title
        reviewBody = view.findViewById(R.id.review_body)
        reviewTitle = view.findViewById(R.id.review_title)

        // Find Buttons
        submitButton = view.findViewById(R.id.submit_review)
        deleteButton = view.findViewById(R.id.delete_review)

        // Listen for rating changes
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            userRating = rating.toDouble()  // Store the selected rating
        }

        lateinit var gameName: String
        lateinit var imageUrl: String

        //Loads game info for the review
        val TAG = "ReviewDB"
        val gameDatabase = firestore.collection("games").document(gameID)
        gameDatabase.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    Log.d(TAG, "${document.data}")
                    gameName = document.getString("name").toString()
                    imageUrl = document.getString("image").toString()
                    Glide.with(view.context)
                        .load(imageUrl)
                        .placeholder(R.drawable.placeholder_game_cover)
                        .error(R.drawable.error_game_cover)
                        .into(imageView)
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with", exception)
            }
        if (userID != "null") {
            val reviewDatabase = firestore.collection("games").document(gameID)
                .collection("reviews").document(userID)
            reviewDatabase.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        Log.d(TAG, "${document.data}")
                        reviewTitle.text = document.getString("reviewTitle")
                        reviewBody.text = document.getString("reviewBody")
                        val rating = document.getDouble("rating")
                        //Change Rating
                        if (rating != null) {
                            ratingBar.setRating(rating.toFloat())
                            submitButton.text = "Change Review"
                            deleteButton.visibility = View.VISIBLE
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ReviewPage", "get failed with", exception)
                }
            val userDatabase = firestore.collection("users").document(userID)
            userDatabase.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        username = document.getString("username").toString()
                    }
                }
        }
        else{
            submitButton.text = "Create Account"
        }
        //TODO find if review exists in userr then change SubmitButton to ChangeReview
        //Todo find if logged in user if not then show createAccount and go to CreatePage

        // Handle submit button click
        submitButton.setOnClickListener {
            val submitReview = ReviewModel(
                gameID,
                userID,
                imageUrl,
                gameName,
                userRating,
                reviewTitle.text.toString(),
                reviewBody.text.toString(),
                0.0,
                username
            )
            //if submitButton.text == "+Submit Review"
            if (submitButton.text == "+Submit Review"){
                //Populate Game Review Db
                firestore.collection("games").document(gameID).collection("reviews")
                    .document(userID).set(submitReview)
                //Populate User Activity Review Db TODO = maintain favorite status
                firestore.collection("users").document(userID).collection("reviews")
                    .document(gameID).set(submitReview, SetOptions.merge())
                Snackbar.make(view, "Submitted Review for ${gameName}", Snackbar.LENGTH_SHORT).show()
                submitButton.text = "Change Review"
                deleteButton.visibility = View.VISIBLE
            }
            else if (submitButton.text == "Change Review"){
                //Update Game Review Db
                firestore.collection("games").document(gameID).collection("reviews")
                    .document(userID).set(submitReview)
                //Update user Activity Review Db
                firestore.collection("users").document(userID).collection("games")
                    .document(gameID).set(submitReview, SetOptions.merge())
                Snackbar.make(view, "Updated Review for ${gameName}", Snackbar.LENGTH_SHORT).show()
            }
            else if(submitButton.text == "Create Account"){
                loadFragment(CreateAccountFragment())
            }
            //else if submitButton.text == "+Create Account"
            //Not sure what to do for ReviewID yet

        }

        deleteButton.setOnClickListener {
            //Delete review from games db
            firestore.collection("games").document(gameID).collection("reviews")
                .document(userID).delete()
            //Delete from user db
            firestore.collection("users").document(userID).collection("reviews")
                .document(gameID).delete()
            Snackbar.make(view, "Deleted Review for ${gameName}", Snackbar.LENGTH_SHORT).show()
            reviewTitle.text = null
            reviewBody.text = null
            ratingBar.rating = 0.0F
            deleteButton.visibility = View.GONE
            submitButton.text = "+Submit Review"
        }
    }

    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }
}