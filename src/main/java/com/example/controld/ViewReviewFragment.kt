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
import com.example.controld.data.model.LikeReviewModel
import com.example.controld.data.model.ReviewModel
import com.google.android.material.snackbar.Snackbar
import co.ankurg.expressview.OnCheckListener
import co.ankurg.expressview.ExpressView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlin.properties.Delegates


class ViewReviewFragment(gameID: String, visituserID: String) : Fragment() {

    private lateinit var imageView : ImageView
    private lateinit var ratingBar: RatingBar
    private var gameID = gameID
    private var visituserID = visituserID
    private lateinit var reviewBody: TextView
    private lateinit var reviewTitle: TextView
    private lateinit var likeNumber: TextView
    private lateinit var likeButton: ExpressView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //view homepage variable
        var v = inflater.inflate(R.layout.fragment_view_review, container, false)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("ViewingReviews", visituserID)
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = context?.getSharedPreferences(
            getString(R.string.accountPrefKey),
            MODE_PRIVATE
        )
        val userID = sharedPref?.getString(getString(R.string.emailKey), "null") ?: "null"

        val firestore = Firebase.firestore

        imageView = view.findViewById(R.id.imageView)

        // Find RatingBar from layout
        ratingBar = view.findViewById(R.id.ratingBar)

        //Find BodyText and Title
        reviewBody = view.findViewById(R.id.review_body)
        reviewTitle = view.findViewById(R.id.review_title)
        likeNumber = view.findViewById(R.id.like_number)

        // Find Buttons
        likeButton = view.findViewById(R.id.like_button)

        lateinit var gameName: String
        lateinit var imageUrl: String
        var likeAmount: Double = 0.0
        lateinit var userName: String

        //Load count of number of


        //Loads game info for the review
        val TAG = "ViewReviewDB"
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
        if (visituserID != "null") {
            val reviewDatabase = firestore.collection("games").document(gameID)
                .collection("reviews").document(visituserID)
            reviewDatabase.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        Log.d(TAG, "${document.data}")
                        reviewTitle.text = document.getString("reviewTitle")
                        reviewBody.text = document.getString("reviewBody")
                        val likes = document.getDouble("numberOfLikes")
                        val rating = document.getDouble("rating")
                        Log.d(TAG, "${rating}")
                        if (likes != null) {
                            likeNumber.text = "${likes.toInt().toString()} likes"
                            likeAmount = likes
                        }
                        //Change Rating
                        if (rating != null) {
                            ratingBar.rating = rating.toFloat()
                        }

                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ReviewPage", "get failed with", exception)
                }
            //Retrieve Username
            val userDatabase = firestore.collection("users").document(visituserID)
            userDatabase.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        Log.d(TAG, "${document.data}")
                        userName = document.getString("username").toString()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ReviewPage", "get failed with", exception)
                }
            //Check if Liked review or not
            val likeDatabase = firestore.collection("users").document(userID).collection("likes")
                .document(gameID + visituserID)
            likeDatabase.get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        Log.d(TAG, "${document.data}")
                        val documentExistsCheck = document.getString("username")
                        if (documentExistsCheck != null) {
                            likeButton.isChecked = true
                        } else {
                            likeButton.isChecked = false
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("ReviewPage", "get failed with", exception)
                    likeButton.isChecked = true
                }
        }

        likeButton.setOnCheckListener(object : OnCheckListener {
            override fun onChecked(view2: ExpressView?) {
                if (userID == "null") {
                    loadFragment(CreateAccountFragment())
                }
                else{
                    Log.d("likeButton", "Checked")
                    val likeReview = LikeReviewModel(
                        gameID,
                        visituserID,
                        imageUrl,
                        gameName,
                        userName
                    )
                    likeAmount += 1
                    //Add Likes to the number of likes in game DB
                    firestore.collection("games").document(gameID).collection("reviews")
                        .document(visituserID).update("numberOfLikes", likeAmount)
                    //Populate User Activity Review Db Likes
                    firestore.collection("users").document(userID).collection("likes")
                        .document(gameID + visituserID).set(likeReview)
                    Snackbar.make(view, "Liked Review for ${gameName}", Snackbar.LENGTH_SHORT).show()
                    likeNumber.text = "${likeAmount.toInt()} likes"
                }
            }

            override fun onUnChecked(view2: ExpressView?) {
                if (userID == "null") {
                    loadFragment(CreateAccountFragment())
                }
                Log.d("likeButton", "Unchecked")
                likeAmount -= 1
                //Update Game Review Db subtracting one like
                firestore.collection("games").document(gameID).collection("reviews")
                    .document(visituserID).update("numberOfLikes", likeAmount)
                //Delete liked review from user db
                firestore.collection("users").document(userID).collection("likes")
                    .document(gameID + visituserID).delete()
                Snackbar.make(view, "Unliked Review for ${gameName}", Snackbar.LENGTH_SHORT).show()
                likeNumber.text = "${likeAmount.toInt()} likes"
            }
        })
    }
    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }
}