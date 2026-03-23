package com.example.controld.com.example.controld

//TODO: Implement lists in the profile tab but also allow the button on the home screen to go to it
//TODO: Lists should allow the user to create grouping of games that they have played or not played
//TODO: What should be a part of lists????
//TODO: In the page for a specific game make it easy to add to favorite or add to
// list using the buttons that are already there
//TODO: hook up the buttons on the home page to their corresponding places
//TODO: allow the user to push the text for each of the categories in the profile tab for the user
//TODO: Allow the user to click on the game in their profile to bring them to the review
// page for the game, the same one that is on the home screen


import com.example.controld.Game
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.util.Date

data class GameList(
    @DocumentId val id: String = "",
    val userId: String = "",  // Owner of the list
    val name: String = "",
    val description: String = "",
    val isPublic: Boolean = false,
    val items: List<GameListItem> = emptyList(),  // Changed from 'games' to 'items' to match Firestore
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val coverGameId: String = "",  // ID of the game to use as cover image
    val creatorId: String = "",    // Added to match Firestore
    val creatorName: String = ""   // Added to match Firestore
)

data class GameListItem(
    val id: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val gameCoverUrl: String = "",
    val addedAt: Date = Date(),
    val notes: String = ""
)

