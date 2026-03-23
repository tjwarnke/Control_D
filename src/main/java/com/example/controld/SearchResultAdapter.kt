package com.example.controld

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import com.example.controld.com.example.controld.GameList
import com.example.controld.com.example.controld.GameListItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.Toast

class SearchResultAdapter(
    private val onItemClick: (SearchResult) -> Unit,
    private val preferredListId: String? = null,
    private val isAddToListMode: Boolean = false
) : ListAdapter<SearchResult, SearchResultAdapter.SearchResultViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)

    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.resultTitle)
        private val subtitleText: TextView = itemView.findViewById(R.id.resultSubtitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.resultDescription)
        private val gameImage: ImageView = itemView.findViewById(R.id.resultImage)
        private val firestore = Firebase.firestore

        @SuppressLint("SetTextI18n")
        fun bind(result: SearchResult) {
            titleText.text = result.title

            
            // Handle specific types differently
            when (result) {
                is SearchResult.SteamGame -> {
                    Log.d("SearchAdapter", "Binding Steam game: ${result.title}")
                    
                    // Add Steam logo badge or indicator
                    gameImage.visibility = View.VISIBLE
                    gameImage.setImageResource(R.drawable.ic_steam)
                    
                    // Show developer/publisher if available, otherwise show default subtitle
                    if (result.developer.isNotEmpty() && result.publisher.isNotEmpty()) {
                        subtitleText.text = "${result.developer} • ${result.publisher}"
                    } else if (result.developer.isNotEmpty()) {
                        subtitleText.text = result.developer
                    } else if (result.publisher.isNotEmpty()) {
                        subtitleText.text = result.publisher
                    } else {
                        subtitleText.text = result.subtitle
                    }
                    
                    // Show playtime and last played info
                    var description = result.description
                    if (result.lastPlayed != null && result.lastPlayed > 0) {
                        val lastPlayedDate = Date(result.lastPlayed.toLong() * 1000)
                        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        description += " • Last played: ${dateFormat.format(lastPlayedDate)}"
                    }
                    descriptionText.text = description

                    // Load Steam game image
                    Log.d("SearchAdapter", "Loading image for ${result.title}: ${result.imageUrl}")
                    Glide.with(itemView.context)
                        .load(result.imageUrl)
                        .placeholder(R.drawable.placeholder_game_cover)
                        .error(R.drawable.error_game_cover)
                        .apply(RequestOptions().centerCrop())
                        .into(gameImage)
                }
                else -> {
                    // Default handling for other types
                    gameImage.visibility = View.GONE
                    subtitleText.text = result.subtitle
                    descriptionText.text = result.description
                    
                    // Load image using Glide
                    Glide.with(itemView.context)
                        .load(result.imageUrl)
                        .placeholder(R.drawable.placeholder_game_cover)
                        .error(R.drawable.error_game_cover)
                        .apply(RequestOptions().centerCrop())
                        .into(gameImage)
                }
            }

            // Set a better background for the type icon
            gameImage.let {
                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.OVAL
                drawable.setColor(Color.WHITE)
                drawable.setStroke(2, Color.parseColor("#E0E0E0"))
                it.background = drawable
            }

            // Set up item click listener
            itemView.setOnClickListener { onItemClick(result) }
            
            // Add long press gesture to show add to list option
            itemView.setOnLongClickListener {
                showAddToListDialog(itemView.context, result)
                true
            }
        }
        
        private fun showAddToListDialog(context: Context, game: SearchResult) {
            // Get current user ID
            val sharedPref = context.getSharedPreferences(
                context.getString(R.string.accountPrefKey), 
                Context.MODE_PRIVATE
            )
            val userId = sharedPref.getString(context.getString(R.string.emailKey), "") ?: ""
            
            if (userId.isBlank()) {
                // User not logged in
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.lists_error)
                    .setMessage(R.string.error_not_logged_in)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }
            
            // If we're in add-to-list mode with a specific list ID, add directly to that list
            if (isAddToListMode && preferredListId != null) {
                // Fetch the list and add the game directly
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val listDoc = withContext(Dispatchers.IO) {
                            firestore.collection("lists").document(preferredListId).get().await()
                        }
                        
                        val list = listDoc.toObject(GameList::class.java)
                        if (list != null) {
                            addGameToList(context, list, game)
                        } else {
                            Toast.makeText(context, "Could not find the specified list", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error accessing list: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
            
            // Otherwise, load all the user's lists and show the selection dialog
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val listsSnapshot = withContext(Dispatchers.IO) {
                        firestore.collection("lists")
                            .whereEqualTo("userId", userId)
                            .get()
                            .await()
                    }
                    
                    val userLists = listsSnapshot.documents.mapNotNull { doc ->
                        doc.toObject(GameList::class.java)
                    }
                    
                    // Show dialog to select a list or create a new one
                    val listNames = userLists.map { it.name }.toMutableList()
                    listNames.add("Create new list") // Add option to create a new list
                    
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_select_list)
                        .setItems(listNames.toTypedArray()) { _, which ->
                            if (which == listNames.size - 1) {
                                // Last item is "Create new list"
                                showCreateListDialog(context, userId, game)
                            } else {
                                val selectedList = userLists[which]
                                addGameToList(context, selectedList, game)
                            }
                        }
                        .setNegativeButton(R.string.lists_cancel, null)
                        .show()
                    
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_error)
                        .setMessage(R.string.error_loading_lists)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
        
        private fun showCreateListDialog(context: Context, userId: String, game: SearchResult) {
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_create_list, null)

            val nameInput = dialogView.findViewById<TextInputEditText>(R.id.list_name_input)
            val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.list_description_input)
            val isPublicSwitch = dialogView.findViewById<SwitchMaterial>(R.id.public_switch)

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lists_create_list)
                .setView(dialogView)
                .setPositiveButton(R.string.lists_create) { dialog, _ ->
                    val name = nameInput.text.toString()
                    val description = descriptionInput.text.toString()
                    val isPublic = isPublicSwitch.isChecked

                    if (name.isNotBlank()) {
                        createListAndAddGame(context, userId, name, description, isPublic, game)
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, R.string.lists_name_required, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.lists_cancel, null)
                .show()
        }
        
        private fun createListAndAddGame(
            context: Context, 
            userId: String, 
            name: String, 
            description: String, 
            isPublic: Boolean,
            game: SearchResult
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Create new game list item
                    val gameItem = GameListItem(
                        id = game.id,
                        gameId = game.id,
                        gameTitle = game.title,
                        gameCoverUrl = game.imageUrl,
                        addedAt = Date()
                    )
                    
                    // Create a new list with this game
                    val newList = GameList(
                        userId = userId,
                        name = name,
                        description = description,
                        isPublic = isPublic,
                        items = listOf(gameItem),
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                    
                    // Add to Firestore
                    val docRef = withContext(Dispatchers.IO) {
                        firestore.collection("lists")
                            .add(newList)
                            .await()
                    }
                    
                    // Show success message
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_success)
                        .setMessage(context.getString(R.string.lists_game_added, game.title, name))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_error)
                        .setMessage(R.string.lists_error_adding_game)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
        
        private fun addGameToList(context: Context, list: GameList, game: SearchResult) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Create new game list item
                    val gameItem = GameListItem(
                        id = game.id,
                        gameId = game.id,
                        gameTitle = game.title,
                        gameCoverUrl = game.imageUrl,
                        addedAt = Date()
                    )
                    
                    // Add to the list of games
                    val updatedItems = list.items.toMutableList()
                    
                    // Check if game is already in the list
                    if (updatedItems.any { it.gameId == game.id }) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.lists_error)
                            .setMessage(R.string.lists_game_already_exists)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        return@launch
                    }
                    
                    updatedItems.add(gameItem)
                    
                    // Update Firestore
                    withContext(Dispatchers.IO) {
                        firestore.collection("lists").document(list.id)
                            .update(
                                mapOf(
                                    "items" to updatedItems,
                                    "updatedAt" to Date()
                                )
                            )
                            .await()
                    }
                    
                    // Show success message
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_success)
                        .setMessage(context.getString(R.string.lists_game_added, game.title, list.name))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.lists_error)
                        .setMessage(R.string.lists_error_adding_game)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.id == newItem.id && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem == newItem
        }
    }
} 