package com.example.controld

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import androidx.navigation.fragment.findNavController

// Data class for ListItem
data class ListItem(
    val id: String = "",
    val gameId: String = "",
    val gameTitle: String = "",
    val gameCoverUrl: String = "",
    val notes: String = "",
    val addedAt: com.google.firebase.Timestamp? = null
)

// Data class for GameList
data class GameList(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val userId: String = "",
    val creatorName: String = "",
    val coverImageUrl: String? = null,
    val isPublic: Boolean = true,
    val items: List<ListItem> = emptyList()
)

/**
 * Fragment for displaying details of a game list
 */
class ListDetailsFragment : Fragment() {
    private val TAG = "ListDetailsFragment"
    private val db = Firebase.firestore
    private lateinit var listTitle: TextView
    private lateinit var listDescription: TextView
    private lateinit var listCoverImage: ShapeableImageView
    private lateinit var creatorInfo: TextView
    private lateinit var itemCount: TextView
    private lateinit var listItemsRecyclerView: RecyclerView
    private lateinit var publicSwitch: SwitchMaterial
    private lateinit var listItemsAdapter: ListItemAdapter

    private var listId: String = ""
    private var creatorId: String = ""
    private var currentUserId: String = ""
    private var listListener: com.google.firebase.firestore.ListenerRegistration? = null

    companion object {
        private const val ARG_LIST_ID = "list_id"

        fun newInstance(listId: String): ListDetailsFragment {
            val fragment = ListDetailsFragment()
            val args = Bundle()
            args.putString(ARG_LIST_ID, listId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            listId = it.getString(ARG_LIST_ID, "")
        }
        
        // Get current user ID
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        listTitle = view.findViewById(R.id.list_title)
        listDescription = view.findViewById(R.id.list_description)
        listCoverImage = view.findViewById(R.id.list_cover_image)
        creatorInfo = view.findViewById(R.id.creator_info)
        itemCount = view.findViewById(R.id.item_count)
        listItemsRecyclerView = view.findViewById(R.id.list_items_recycler_view)
        publicSwitch = view.findViewById(R.id.public_switch)

        // Setup RecyclerView
        listItemsRecyclerView.layoutManager = LinearLayoutManager(context)
        listItemsAdapter = ListItemAdapter(emptyList())
        listItemsRecyclerView.adapter = listItemsAdapter
        
        // Setup back button
        view.findViewById<MaterialButton>(R.id.back_button).setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        
        // Setup public switch
        publicSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateListVisibility(listId, isChecked)
        }
        
        // Load list data
        if (listId.isNotEmpty()) {
            setupListListener(listId)
        } else {
            Log.e(TAG, "No list ID provided")
            Snackbar.make(requireView(), "Error: No list ID provided", Snackbar.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove the listener when the fragment is destroyed
        listListener?.remove()
    }

    private fun setupListListener(listId: String) {
        Log.d(TAG, "Setting up listener for list: $listId")
        // Remove any existing listener
        listListener?.remove()
        
        // Set up a new listener that will update the UI whenever the list changes
        listListener = db.collection("lists").document(listId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error loading list: ${e.message}", e)
                    Snackbar.make(requireView(), "Error loading list: ${e.message}", Snackbar.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Received list update from Firestore")
                    Log.d(TAG, "Raw snapshot data: ${snapshot.data}")
                    
                    try {
                        val list = snapshot.toObject(GameList::class.java)
                        Log.d(TAG, "List data: name=${list?.name}, items count=${list?.items?.size}")
                        Log.d(TAG, "List cover image URL: ${list?.coverImageUrl}")
                        Log.d(TAG, "List creator: userId=${list?.userId}, creatorName=${list?.creatorName}")
                        
                        // Log the first few items if available
                        list?.items?.take(3)?.forEachIndexed { index, item ->
                            Log.d(TAG, "Item $index: id=${item.id}, title=${item.gameTitle}, imageUrl=${item.gameCoverUrl}")
                        }
                        
                        list?.let {
                            listTitle.text = it.name
                            listDescription.text = it.description
                            
                            // Fetch the username from the users collection
                            val creatorId = it.userId
                            if (creatorId.isNotEmpty()) {
                                db.collection("users").document(creatorId)
                                    .get()
                                    .addOnSuccessListener { userDoc ->
                                        if (userDoc != null && userDoc.exists()) {
                                            val username = userDoc.getString("username") ?: creatorId
                                            creatorInfo.text = "Created by $username"
                                        } else {
                                            creatorInfo.text = "Created by ${it.creatorName.takeIf { name -> name.isNotEmpty() } ?: creatorId}"
                                        }
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.e(TAG, "Error fetching username: ${exception.message}", exception)
                                        creatorInfo.text = "Created by ${it.creatorName.takeIf { name -> name.isNotEmpty() } ?: creatorId}"
                                    }
                            } else {
                                creatorInfo.text = "Created by ${it.creatorName.takeIf { name -> name.isNotEmpty() } ?: "Unknown"}"
                            }
                            
                            itemCount.text = "${it.items.size} items"
                            publicSwitch.isChecked = it.isPublic
                            
                            // Show/hide public switch based on whether current user is the creator
                            publicSwitch.visibility = if (currentUserId == it.userId) View.VISIBLE else View.GONE
                            
                            // Load cover image if available
                            if (!it.coverImageUrl.isNullOrEmpty()) {
                                Log.d(TAG, "Loading cover image from URL: ${it.coverImageUrl}")
                                Glide.with(requireContext())
                                    .load(it.coverImageUrl)
                                    .placeholder(R.drawable.placeholder_game_cover)
                                    .into(listCoverImage)
                            } else if (it.items.isNotEmpty()) {
                                // If no cover image but we have items, use the first item's image
                                val firstItem = it.items.first()
                                Log.d(TAG, "First item image URL: ${firstItem.gameCoverUrl}")
                                if (!firstItem.gameCoverUrl.isNullOrEmpty()) {
                                    Log.d(TAG, "Using first item image as cover: ${firstItem.gameCoverUrl}")
                                    Glide.with(requireContext())
                                        .load(firstItem.gameCoverUrl)
                                        .placeholder(R.drawable.placeholder_game_cover)
                                        .into(listCoverImage)
                                } else {
                                    Log.d(TAG, "First item has no image URL, using placeholder")
                                    listCoverImage.setImageResource(R.drawable.placeholder_game_cover)
                                }
                            } else {
                                Log.d(TAG, "No items in list, using placeholder for cover")
                                listCoverImage.setImageResource(R.drawable.placeholder_game_cover)
                            }
                            
                            // Update the adapter with the new items
                            Log.d(TAG, "Updating adapter with ${it.items.size} items")
                            listItemsAdapter.updateItems(it.items)
                        } ?: run {
                            Log.e(TAG, "Failed to convert snapshot to GameList object")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception while processing list data", e)
                        e.printStackTrace()
                    }
                } else {
                    Log.e(TAG, "List document does not exist")
                }
            }
    }

    private fun updateListVisibility(listId: String, isPublic: Boolean) {
        val db = FirebaseFirestore.getInstance()
        db.collection("lists").document(listId)
            .update("isPublic", isPublic)
            .addOnSuccessListener {
                Snackbar.make(requireView(), 
                    "List is now ${if (isPublic) "public" else "private"}", 
                    Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), 
                    "Error updating list visibility: ${e.message}", 
                    Snackbar.LENGTH_LONG).show()
                // Revert the switch state on failure
                publicSwitch.isChecked = !isPublic
            }
    }
    
    // Updated adapter for list items
    inner class ListItemAdapter(private var items: List<ListItem>) : 
        RecyclerView.Adapter<ListItemAdapter.ViewHolder>() {
        
        fun updateItems(newItems: List<ListItem>) {
            Log.d(TAG, "Adapter updating items: ${newItems.size} items")
            items = newItems
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            Log.d(TAG, "Creating new ViewHolder")
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_list_game, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            Log.d(TAG, "Binding item at position $position: ${items[position].gameTitle}")
            holder.bind(items[position])
        }
        
        override fun getItemCount(): Int {
            Log.d(TAG, "Getting item count: ${items.size}")
            return items.size
        }
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleTextView: TextView = itemView.findViewById(R.id.game_title)
            private val gameImageView: ShapeableImageView = itemView.findViewById(R.id.game_image)
            private val noteTextView: TextView = itemView.findViewById(R.id.game_note)
            
            fun bind(item: ListItem) {
                Log.d(TAG, "Binding ViewHolder for game: ${item.gameTitle}")
                Log.d(TAG, "Game details: id=${item.id}, gameId=${item.gameId}, imageUrl=${item.gameCoverUrl}")
                
                titleTextView.text = item.gameTitle
                noteTextView.text = item.notes
                
                // Load image using Glide
                if (!item.gameCoverUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Loading game image from URL: ${item.gameCoverUrl}")
                    try {
                        Glide.with(itemView.context)
                            .load(item.gameCoverUrl)
                            .placeholder(R.drawable.placeholder_game_cover)
                            .into(gameImageView)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception while loading image for ${item.gameTitle}", e)
                        gameImageView.setImageResource(R.drawable.placeholder_game_cover)
                    }
                } else {
                    // Set placeholder if no image URL
                    Log.d(TAG, "No image URL for ${item.gameTitle}, using placeholder")
                    gameImageView.setImageResource(R.drawable.placeholder_game_cover)
                }
                
                itemView.setOnClickListener {
                    Log.d(TAG, "Game item clicked: ${item.gameTitle}")
                    // Navigate to game details
                    val gameReviewFragment = GameReviewFragment.newInstance(item.gameId)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, gameReviewFragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
    }
} 