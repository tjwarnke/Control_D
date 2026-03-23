package com.example.controld.data.model

import com.google.firebase.firestore.FieldValue

data class GameActivityModel(
    var gameID: String,
    var image: String,
    var gamename: String,
    val favorited: Boolean = true,
    var dateAdded: FieldValue = FieldValue.serverTimestamp()
)


