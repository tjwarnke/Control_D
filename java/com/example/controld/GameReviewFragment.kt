package com.example.controld

import ReviewListFragment
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.controld.data.model.GameActivityModel
import com.example.controld.helper.dateToStringFullMonth
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.controld.steam.SteamGameManager
import com.example.controld.steam.SteamApiClient
import java.util.*
import android.widget.RatingBar
import com.example.controld.com.example.controld.GameList
import com.example.controld.com.example.controld.GameListItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

class GameReviewFragment : Fragment() {

    private val TAG = "GameReviewFragment"
    private lateinit var firestore: FirebaseFirestore
    private var imageUrl: String? = null
    private var db: String = ""
    private lateinit var gameName: TextView
    private lateinit var publisher: TextView
    private lateinit var description: TextView
    private lateinit var userID: String
    private lateinit var stars: RatingBar
    private lateinit var starText: TextView
    private lateinit var reviewCountText: TextView
    private lateinit var gameidentifier: String
  
    //private lateinit var genres: Array
    private lateinit var genre1: TextView
    private lateinit var genre2: TextView
    private lateinit var genre3: TextView
    private lateinit var steamGameManager: SteamGameManager

    // define button
    private lateinit var createReviewButton: Button
    private lateinit var favoriteButton: MaterialButton
    private lateinit var addToListButton: Button

    private lateinit var gameImageView: ImageView
    private lateinit var releaseDate: TextView
    private lateinit var viewReviewsPageButton: Button


    private lateinit var auth: FirebaseAuth
    private var gameId: String? = null
    private var isSteamGame: Boolean = false
    private var steamGameData: SearchResult.SteamGame? = null

    companion object {
        private const val ARG_GAME_ID = "gameId"
        private const val ARG_IS_STEAM_GAME = "isSteamGame"
        private const val ARG_STEAM_GAME_DATA = "steamGameData"
        private const val ARG_TITLE = "title"
        private const val ARG_PUBLISHER = "publisher"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_IMAGE_URL = "imageUrl"
        private const val ARG_RELEASE_DATE = "releaseDate"
        private const val ARG_DEVELOPER = "developer"
        private const val ARG_SUBTITLE = "subtitle"

        fun newInstance(
            gameId: String,
            isSteamGame: Boolean = false,
            steamGameData: SearchResult.SteamGame? = null
        ): GameReviewFragment {
            Log.d("GameReviewFragment", "Creating new instance with gameId: $gameId, isSteamGame: $isSteamGame")
            val fragment = GameReviewFragment()
            val args = Bundle().apply {
                putString(ARG_GAME_ID, gameId)
                putBoolean(ARG_IS_STEAM_GAME, isSteamGame)
                if (steamGameData != null) {
                    Log.d("GameReviewFragment", "Adding Steam game data: ${steamGameData.title}")
                    Log.d("GameReviewFragment", "Steam game release date: ${steamGameData.releaseDate}")
                    putString(ARG_TITLE, steamGameData.title)
                    putString(ARG_PUBLISHER, steamGameData.publisher)
                    putString(ARG_DESCRIPTION, steamGameData.description)
                    putString(ARG_IMAGE_URL, steamGameData.imageUrl)
                    putString(ARG_DEVELOPER, steamGameData.developer)
                    putString(ARG_SUBTITLE, steamGameData.subtitle)
                    putString(ARG_RELEASE_DATE, steamGameData.releaseDate)
                }
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        try {
            arguments?.let {
                db = it.getString(ARG_GAME_ID, "") ?: ""
                isSteamGame = it.getBoolean(ARG_IS_STEAM_GAME, false)
                Log.d(TAG, "Arguments parsed - db: $db, isSteamGame: $isSteamGame")
            } ?: run {
                Log.e(TAG, "No arguments provided to fragment")
            }
            
            // Initialize SteamGameManager
            steamGameManager = SteamGameManager(SteamApiClient.create())
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        try {
            val v = inflater.inflate(R.layout.fragment_game_review, container, false)
            gameName = v.findViewById(R.id.gameName)
            publisher = v.findViewById(R.id.publisherString)
            description = v.findViewById(R.id.gameDescription)
            releaseDate = v.findViewById(R.id.releaseDate)
            firestore = Firebase.firestore
            gameImageView = v.findViewById(R.id.gameImageView)
            stars = v.findViewById(R.id.ratingBar)
            starText = v.findViewById(R.id.starText)
            reviewCountText = v.findViewById(R.id.reviewCount)
            genre1 = v.findViewById(R.id.genre1text)
            genre2 = v.findViewById(R.id.genre2text)
            genre3 = v.findViewById(R.id.genre3text)
            Log.d(TAG, "Views initialized successfully")
            return v
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView: ${e.message}", e)
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            val sharedPref = context?.getSharedPreferences(
                getString(R.string.accountPrefKey),
                MODE_PRIVATE
            )
            userID = sharedPref?.getString(getString(R.string.emailKey), "null") ?: "null"
            Log.d(TAG, "User ID loaded: $userID")

            createReviewButton = view.findViewById(R.id.review_button)
            favoriteButton = view.findViewById(R.id.favoriteButton)
            addToListButton = view.findViewById(R.id.addToListButton)
            viewReviewsPageButton = view.findViewById(R.id.view_reviews_page)
            firestore = Firebase.firestore
            auth = FirebaseAuth.getInstance()
            Log.d(TAG, "UI elements and services initialized")


        //Check if current game is favorited or not. If not favorited, current button state is fine
        //if game is favorited the button should instead say unfavorited and remove the game from your activity item.
        //Check if user logged in, if so check if they have the game as favorited
        if (userID != null) {
            //val favorited =
            //    firestore.collection("users").document(userID).collection("games").document(gameId)
            //favorited.get()
            // Get arguments
            gameId = arguments?.getString(ARG_GAME_ID)
            isSteamGame = arguments?.getBoolean(ARG_IS_STEAM_GAME) ?: false
            Log.d(TAG, "Arguments retrieved - gameId: $gameId, isSteamGame: $isSteamGame")

            if (isSteamGame) {
                Log.d(TAG, "Handling Steam game data")
                // Handle Steam game data
                val title = arguments?.getString(ARG_TITLE)
                val imageUrlText = arguments?.getString(ARG_IMAGE_URL)

                Log.d(TAG, "Steam game data - title: $title, gameId: $gameId")

                // Populate UI with Steam game data
                gameName.text = title ?: "Unknown Game"

                // Set the imageUrl for Steam games
                imageUrl = imageUrlText
                Log.d(TAG, "Set imageUrl for Steam game: $imageUrl")

                // For Steam games, fetch the detailed information from the Steam API
                gameId?.let { id ->
                    Log.d(TAG, "Starting Steam API fetch for game ID: $id")
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Log.d(TAG, "Calling Steam API getGameDetails for game ID: $id")
                            val gameDetails = steamGameManager.getGameDetails(id.toLong())
                            Log.d(
                                TAG,
                                "Steam API response received - success: ${gameDetails != null}"
                            )

                            withContext(Dispatchers.Main) {
                                // Update description
                                val descriptionText = gameDetails?.description
                                Log.d(
                                    TAG,
                                    "Game description from API: ${descriptionText?.take(100)}..."
                                )
                                description.text = descriptionText ?: "No description available"

                                // Update publisher
                                val publishers = gameDetails?.publishers
                                Log.d(TAG, "Game publishers from API: $publishers")
                                val publisherText = publishers?.firstOrNull() ?: "Unknown Publisher"
                                publisher.text = publisherText

                                // Update release date from Steam game data if available
                                val releaseDateText = arguments?.getString(ARG_RELEASE_DATE)
                                Log.d(TAG, "Release date from arguments: $releaseDateText")
                                Log.d(
                                    TAG,
                                    "Steam game data release date: ${steamGameData?.releaseDate}"
                                )
                                releaseDate.text = releaseDateText ?: steamGameData?.releaseDate
                                        ?: "Release date unknown"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching game details", e)
                            Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                            withContext(Dispatchers.Main) {
                                description.text = "No description available"
                                publisher.text = "Unknown Publisher"
                                releaseDate.text = "Release date unknown"
                            }
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Game ID is null, cannot fetch Steam details")
                }

                if (imageUrlText != null) {
                    Log.d(TAG, "Loading Steam game image: $imageUrlText")
                    Glide.with(this)
                        .load(imageUrlText)
                        .placeholder(R.drawable.placeholder_game_cover)
                        .error(R.drawable.error_game_cover)
                        .into(gameImageView)
                } else {
                    Log.e(TAG, "Steam game image URL is null")
                }

                // Check if game is favorited
                checkIfFavorited()
            } else {
                Log.d(TAG, "Handling regular game data")
                // Handle regular game from database
                gameId?.let { id ->
                    Log.d(TAG, "Fetching game data from Firestore for ID: $id")
                    var gameDatabase = firestore.collection("games").document(id)
                    gameDatabase.get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val gameNameText = document.getString("name")
                                val publisherText = document.getString("publisher")
                                val descriptionText = document.getString("description")
                                val imageURL = document.getString("imageWide")
                                val genres = document.get("genres") as List<String>
                                val rawDate = document.getTimestamp("release")
                                gameidentifier = id
                                releaseDate.text = dateToStringFullMonth(rawDate!!)
                                genre1.text = genres[0]
                                genre2.text = genres[1]
                                if (genres[2] == ""){
                                    genre3.isVisible = false
                                }else{
                                    genre3.isVisible = true
                                    genre3.text = genres[2]
                                }
                                Log.d(TAG, "Game data retrieved - name: $gameNameText, publisher: $publisherText")

                                gameName.text = gameNameText ?: "Unknown Game"
                                publisher.text = publisherText ?: "Unknown Publisher"
                                description.text = descriptionText ?: "No description available"
                                if (imageURL != null) {
                                    imageUrl = imageURL
                                    Log.d(TAG, "Loading game image: $imageURL")
                                    Glide.with(this)
                                        .load(imageURL)
                                        .placeholder(R.drawable.placeholder_game_cover)
                                        .error(R.drawable.error_game_cover)
                                        .into(gameImageView)
                                } else {
                                    Log.e(TAG, "Game image URL is null")
                                }

                                // Check if game is favorited
                                checkIfFavorited()
                            } else {
                                Log.e(TAG, "Game document does not exist in Firestore")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error fetching game data: ${e.message}", e)
                        }

                    gameDatabase.collection("reviews").get()
                        .addOnSuccessListener { reviews ->
                            var ratingSum = 0.0.toFloat()
                            var reviewCount = 0
                            for (review in reviews) {
                                ratingSum += (review.get("rating") as Double).toFloat()
                                reviewCount++
                            }
                            //Get avg
                            if (reviewCount != 0) {
                                ratingSum = ratingSum / reviewCount
                            }
                            stars.rating = ratingSum
                            starText.text = String.format("%.1f", ratingSum)
                            if (reviewCount == 1) {
                                reviewCountText.text = "$reviewCount review"
                            } else {
                                reviewCountText.text = "$reviewCount reviews"
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.d(TAG, "get failed with $exception")
                        }
                } ?: run {
                    Log.e(TAG, "Game ID is null")
                }
            }

            //check if User has created a review for the game
            if (userID != "null") {
                Log.d(TAG, "Checking if user has created a review for game ID: $db")
                val reviewCheck = firestore.collection("games").document(db)
                    .collection("reviews").document(userID)
                reviewCheck.get()
                    .addOnSuccessListener { document ->
                        if (document != null) {
                            if (document.getString("reviewBody") != null) {
                                createReviewButton.text = "View Your Review"
                            } else {
                                createReviewButton.text = "Create Review"
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Review get failed with: ${exception.message}", exception)
                        createReviewButton.text = "Create Review"
                    }
            }

            // Set up onClickListeners
            setupButtonListeners()
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
        }
    }

    private fun setupButtonListeners() {
        // Setup existing buttons
        createReviewButton.setOnClickListener {
            navigateToCreateReview()
        }
        
        favoriteButton.setOnClickListener {
            toggleFavorite()
        }
        
        // Setup addToList button
        view?.findViewById<Button>(R.id.addToListButton)?.setOnClickListener {
            showAddToListDialog()
        }

        viewReviewsPageButton.setOnClickListener {
            val reviewFragment = ReviewListFragment.newInstance(gameidentifier)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, reviewFragment)
                .addToBackStack(null)
                .commit()
        }
    }
    
    private fun showAddToListDialog() {
        if (userID == "null") {
            Snackbar.make(requireView(), "Please sign in to add games to lists", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Starting to load user lists for userID: $userID")
        
        // Load user's lists first
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Querying Firestore for lists with userId: $userID")
                val snapshot = firestore.collection("lists")
                    .whereEqualTo("userId", userID)
                    .get()
                    .await()
                
                Log.d(TAG, "Retrieved ${snapshot.documents.size} lists from Firestore")
                
                val userLists = snapshot.documents.mapNotNull { doc ->
                    val list = doc.toObject(GameList::class.java)
                    Log.d(TAG, "Mapped document ${doc.id} to GameList: ${list?.name}")
                    list?.copy(id = doc.id)
                }
                
                Log.d(TAG, "Successfully mapped ${userLists.size} lists")
                
                withContext(Dispatchers.Main) {
                    if (userLists.isEmpty()) {
                        Log.d(TAG, "No lists found, showing create list dialog")
                        // No lists exist, show create list dialog directly
                        showCreateListDialog()
                    } else {
                        Log.d(TAG, "Showing list selection dialog with ${userLists.size} lists")
                        // Show list selection dialog
                        showListSelectionDialog(userLists)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user lists", e)
                Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), "Error loading lists", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showListSelectionDialog(userLists: List<GameList>) {
        Log.d(TAG, "Setting up list selection dialog with ${userLists.size} lists")
        
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_list, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.lists_recycler_view)
        val createNewListButton = dialogView.findViewById<Button>(R.id.create_new_list_button)
        
        // Set up RecyclerView with user's lists
        val adapter = ListsAdapter { list ->
            Log.d(TAG, "List selected: ${list.name} (ID: ${list.id})")
            // Add game to selected list
            addGameToList(list)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        
        adapter.submitList(userLists)
        Log.d(TAG, "Submitted ${userLists.size} lists to adapter")
        
        // Set up create new list button
        createNewListButton.setOnClickListener {
            Log.d(TAG, "Create new list button clicked")
            // Dismiss this dialog and show create list dialog
            (dialogView.parent as? ViewGroup)?.let { parent ->
                parent.removeView(dialogView)
            }
            showCreateListDialog()
        }
        
        // Show the dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lists_select_list)
            .setView(dialogView)
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }
    
    private fun showCreateListDialog() {
        Log.d(TAG, "Showing create list dialog")
        
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_list, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.list_name_input)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.list_description_input)
        val isPublicSwitch = dialogView.findViewById<SwitchMaterial>(R.id.public_switch)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lists_create_list)
            .setView(dialogView)
            .setPositiveButton(R.string.lists_create) { dialog, _ ->
                val name = nameInput.text.toString()
                val description = descriptionInput.text.toString()
                val isPublic = isPublicSwitch.isChecked

                Log.d(TAG, "Creating new list with name: $name, description: $description, isPublic: $isPublic")

                if (name.isNotBlank()) {
                    createList(name, description, isPublic)
                    dialog.dismiss()
                } else {
                    Log.d(TAG, "List name is blank, showing error toast")
                    Toast.makeText(context, R.string.lists_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }
    
    private fun createList(name: String, description: String, isPublic: Boolean) {
        Log.d(TAG, "Creating new list: $name")
        
        val newList = GameList(
            userId = userID,
            name = name,
            description = description,
            isPublic = isPublic,
            items = emptyList(),
            createdAt = Date(),
            updatedAt = Date()
        )
        
        Log.d(TAG, "New list object created with userId: $userID")
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Adding new list to Firestore")
                val docRef = firestore.collection("lists")
                    .add(newList)
                    .await()
                
                Log.d(TAG, "List added to Firestore with ID: ${docRef.id}")
                
                // Add the game to the newly created list
                val gameToAdd = GameListItem(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId ?: "",
                    gameTitle = gameName.text.toString(),
                    gameCoverUrl = imageUrl ?: "",
                    addedAt = Date()
                )
                
                Log.d(TAG, "Created GameListItem: ${gameToAdd.gameTitle} (ID: ${gameToAdd.id})")
                
                val updatedList = newList.copy(
                    id = docRef.id,
                    items = listOf(gameToAdd)
                )
                
                Log.d(TAG, "Updating list with game: ${gameToAdd.gameTitle}")
                
                firestore.collection("lists")
                    .document(docRef.id)
                    .set(updatedList)
                    .await()
                
                Log.d(TAG, "List updated successfully with game")
                
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), "List created and game added", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating list", e)
                Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), "Error creating list", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun addGameToList(list: GameList) {
        Log.d(TAG, "Adding game to list: ${list.name} (ID: ${list.id})")
        
        if (gameId == null || gameName.text == null) {
            Log.e(TAG, "Game information is missing: gameId=${gameId}, gameName=${gameName.text}")
            Snackbar.make(requireView(), "Game information is missing", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gameToAdd = GameListItem(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId!!,
                    gameTitle = gameName.text.toString(),
                    gameCoverUrl = imageUrl ?: "",
                    addedAt = Date()
                )
                
                Log.d(TAG, "Created GameListItem: ${gameToAdd.gameTitle} (ID: ${gameToAdd.id})")
                
                // Check if game already exists in the list
                val gameExists = list.items.any { it.gameId == gameId }
                Log.d(TAG, "Game exists in list: $gameExists")
                
                if (!gameExists) {
                    val updatedItems = list.items + gameToAdd
                    Log.d(TAG, "Updated items list size: ${updatedItems.size}")
                    
                    // Update the list with the new game
                    Log.d(TAG, "Updating list document: ${list.id}")
                    firestore.collection("lists")
                        .document(list.id)
                        .update(
                            "items", updatedItems,
                            "updatedAt", Date()
                        )
                        .await()
                    
                    Log.d(TAG, "List updated successfully with new game")
                    
                    withContext(Dispatchers.Main) {
                        Snackbar.make(requireView(), "Game added to list", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "Game already exists in list, showing message")
                    withContext(Dispatchers.Main) {
                        Snackbar.make(requireView(), "Game already in this list", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding game to list", e)
                Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), "Error adding game to list", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFavorite() {
        try {
            val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),
                MODE_PRIVATE
            )
            var userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"
            if (userID != "null") {
                Log.d(TAG, "Toggling favorite for user: ${userID}, game: $gameId")
                val favoriteRef = firestore.collection("users").document(userID)
                    .collection("favorites")
                    .document(gameId!!)

                favoriteRef.get().addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Remove from favorites
                        favoriteRef.delete()
                            .addOnSuccessListener {
                                favoriteButton.setIconResource(R.drawable.ic_baseline_favorite_border_24)
                                Snackbar.make(requireView(), "Removed from Favorites", Snackbar.LENGTH_SHORT).show()
                                Log.d(TAG, "Game removed from favorites")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error removing from favorites: ${e.message}", e)
                            }
                    } else {
                        // Add to favorites
                        val gameData = hashMapOf(
                            "gameID" to gameId,
                            "gamename" to gameName.text.toString(),
                            "image" to (imageUrl ?: ""),
                            "dateAdded" to FieldValue.serverTimestamp()
                        )
                        favoriteRef.set(gameData)
                            .addOnSuccessListener {
                                favoriteButton.setIconResource(R.drawable.ic_baseline_favorite_24)
                                Snackbar.make(requireView(), "Added to Favorites", Snackbar.LENGTH_SHORT).show()
                                Log.d(TAG, "Game added to favorites")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error adding to favorites: ${e.message}", e)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking favorite status: ${e.message}", e)
                }
            } else {
                Snackbar.make(requireView(), "Unable to favorite, please sign in to favorite",Snackbar.LENGTH_SHORT).show()
                Log.d(TAG, "User not logged in, cannot toggle favorite")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in toggleFavorite: ${e.message}", e)
        }
    }

    private fun navigateToCreateReview() {
        gameId?.let { id ->
            Log.d(TAG, "Navigating to create review with gameId: $id")
            val fragment = CreateReviewFragment.newInstance(id)
            requireActivity().supportFragmentManager.commit {
                replace(R.id.fragment_container, fragment)
                addToBackStack("game_review")
            }
        }
    }

    private fun checkIfFavorited() {
        try {
            val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey),
                MODE_PRIVATE
            )
            var userID = sharedPref?.getString(getString(R.string.emailKey),"null") ?: "null"
            if (userID != "null") {
                Log.d(TAG, "Checking if game is favorited for user: ${userID}")
                firestore.collection("users").document(userID)
                    .collection("favorites")
                    .document(gameId!!)
                    .get()
                    .addOnSuccessListener { document ->
                        // Update the button icon based on favorite status
                        favoriteButton.setIconResource(
                            if (document.exists()) R.drawable.ic_baseline_favorite_24
                            else R.drawable.ic_baseline_favorite_border_24
                        )
                        Log.d(TAG, "Favorite status updated: ${document.exists()}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking favorite status: ${e.message}", e)
                    }
            } else {
                Log.d(TAG, "User not logged in, cannot check favorite status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkIfFavorited: ${e.message}", e)
        }
    }
}
