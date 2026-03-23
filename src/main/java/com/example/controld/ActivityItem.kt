package com.example.controld

import android.graphics.drawable.Drawable
import java.util.Date

data class ActivityItem(
    val name: String,
    val gameTitle: String,
    val type: String,
    val avatarUrl: String,
    val id: String = "",
    val secondId: String = "",
    val date: Date = Date(),
)

data class AccountContainer(
    val userID: String,
    val userTitle: String,
    val username: String,
    var dateAdded: Date = Date()
){
    fun updateDate(){
        dateAdded = Date()
    }
}
