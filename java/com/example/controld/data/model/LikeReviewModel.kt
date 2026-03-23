package com.example.controld.data.model

import com.google.firebase.firestore.FieldValue

data class LikeReviewModel(
    var gameID: String,
    var userID: String,
    var image: String,
    var gamename: String,
    var username: String,
    var reviewDate: FieldValue = FieldValue.serverTimestamp()
)
