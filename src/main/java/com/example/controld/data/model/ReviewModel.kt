package com.example.controld.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ReviewModel(
    var gameID: String = "",
    var userID: String = "",
    var image: String = "",
    var gamename: String = "",
    var rating: Double = 0.0,
    var reviewTitle: String = "",
    var reviewBody: String = "",
    var numberOfLikes: Double = 0.0,
    var username: String = "",


    // Use @ServerTimestamp to mark this field as a Firestore-managed timestamp
    @ServerTimestamp var reviewDate: Date? = null
) {
    // No-argument constructor
    constructor() : this("", "", "", "", 0.0, "", "", 0.0, "", null)
}
