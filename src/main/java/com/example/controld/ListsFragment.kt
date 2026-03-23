package com.example.controld

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.com.example.controld.GameList
import com.example.controld.com.example.controld.GameListItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ListsFragment : Fragment() {
    private lateinit var listsAdapter: ListsAdapter
    private val lists = mutableListOf<GameList>()
    private val firestore = Firebase.firestore
    private var userId: String = ""
    private lateinit var emptyView: TextView
    private lateinit var titleTextView: TextView
    
    // Properties for list selection mode
    private var listSelectionMode: Boolean = false
    private var gameId: String? = null
    private var gameTitle: String? = null
    private var gameImageUrl: String? = null

    companion object {
        private const val TAG = "ListsFragment"
        
        // Factory method to determine if we're showing user's lists or public lists
        fun newInstance(
            showPublicLists: Boolean = false, 
            listId: String? = null,
            listSelectionMode: Boolean = false,
            gameId: String? = null,
            gameTitle: String? = null,
            gameImageUrl: String? = null
        ): ListsFragment {
            val fragment = ListsFragment()
            val args = Bundle()
            args.putBoolean("showPublicLists", showPublicLists)
            args.putBoolean("listSelectionMode", listSelectionMode)
            
            if (listId != null) {
                args.putString("listId", listId)
            }
            
            if (listSelectionMode) {
                args.putString("gameId", gameId)
                args.putString("gameTitle", gameTitle)
                args.putString("gameImageUrl", gameImageUrl)
            }
            
            fragment.arguments = args
            return fragment
        }
    }

    private val showPublicLists: Boolean
        get() = arguments?.getBoolean("showPublicLists", false) ?: false
        
    private val specificListId: String?
        get() = arguments?.getString("listId")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get current user ID
        val sharedPref = context?.getSharedPreferences(getString(R.string.accountPrefKey), MODE_PRIVATE)
        userId = sharedPref?.getString(getString(R.string.emailKey), "") ?: ""
        
        // Check if we're in list selection mode
        listSelectionMode = arguments?.getBoolean("listSelectionMode", false) ?: false
        if (listSelectionMode) {
            gameId = arguments?.getString("gameId")
            gameTitle = arguments?.getString("gameTitle")
            gameImageUrl = arguments?.getString("gameImageUrl")
        }
        
        // Find views
        emptyView = view.findViewById(R.id.empty_view)
        
        // Set title based on whether we're showing public or user's lists
        view.findViewById<TextView>(R.id.lists_title)?.let {
            titleTextView = it
            titleTextView.text = when {
                listSelectionMode -> getString(R.string.lists_select_list)
                showPublicLists -> getString(R.string.lists_public_lists)
                else -> getString(R.string.lists_my_lists)
            }
        }
        
        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.lists_recycler_view)
        
        if (listSelectionMode) {
            // In selection mode, clicking a list adds the game to that list
            listsAdapter = ListsAdapter { list ->
                addGameToList(list)
            }
        } else {
            // Normal mode, navigates to list detail
            listsAdapter = ListsAdapter { list ->
                // Navigate to list details fragment
                val listDetailsFragment = ListDetailsFragment.newInstance(list.id)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, listDetailsFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listsAdapter
        }

        // Set up FAB for creating new lists (only visible for user's own lists)
        val createListFab = view.findViewById<FloatingActionButton>(R.id.create_list_fab)
        if (showPublicLists) {
            createListFab.visibility = View.GONE
        } else {
            createListFab.setOnClickListener {
                showCreateListDialog()
            }
        }

        // Check if we should load a specific list
        specificListId?.let { listId ->
            openSpecificList(listId)
            return
        }

        // Load lists
        loadLists()
    }
    
    private fun addGameToList(list: GameList) {
        if (gameId == null || gameTitle == null) {
            Toast.makeText(context, "Game information is missing", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gameToAdd = GameListItem(
                    id = UUID.randomUUID().toString(),
                    gameId = gameId!!,
                    gameTitle = gameTitle!!,
                    gameCoverUrl = gameImageUrl ?: "",
                    addedAt = Date()
                )
                
                // Check if game already exists in the list
                val gameExists = list.items.any { it.gameId == gameId }
                
                if (!gameExists) {
                    val updatedItems = list.items + gameToAdd
                    
                    // Update the list with the new game
                    firestore.collection("lists")
                        .document(list.id)
                        .update(
                            "items", updatedItems,
                            "updatedAt", Date()
                        )
                        .await()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.game_added_to_list, Toast.LENGTH_SHORT).show()
                        // Navigate back
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.game_already_in_list, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding game to list", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_adding_game, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun openSpecificList(listId: String) {
        // Navigate directly to the list detail view
        val listDetailsFragment = ListDetailsFragment.newInstance(listId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, listDetailsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showCreateListDialog() {
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

                if (name.isNotBlank()) {
                    createList(name, description, isPublic)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, R.string.lists_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }

    private fun createList(name: String, description: String, isPublic: Boolean) {
        if (userId.isBlank()) {
            Toast.makeText(context, R.string.error_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        val newList = GameList(
            userId = userId,
            name = name,
            description = description,
            isPublic = isPublic,
            items = emptyList(),
            createdAt = Date(),
            updatedAt = Date()
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore.collection("lists")
                    .add(newList)
                    .await()
                    
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.lists_created, Toast.LENGTH_SHORT).show()
                    loadLists() // Reload to get the new list
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating list", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_creating_list, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadLists() {
        if (showPublicLists) {
            loadPublicLists()
        } else {
            loadUserLists()
        }
    }
    
    private fun loadUserLists() {
        if (userId.isBlank()) {
            showEmptyView(true)
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = firestore.collection("lists")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    
                val fetchedLists = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(GameList::class.java)
                }
                
                withContext(Dispatchers.Main) {
                    lists.clear()
                    lists.addAll(fetchedLists)
                    listsAdapter.submitList(lists.toList())
                    showEmptyView(lists.isEmpty())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user lists", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_loading_lists, Toast.LENGTH_SHORT).show()
                    showEmptyView(true)
                }
            }
        }
    }
    
    private fun loadPublicLists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = firestore.collection("lists")
                    .whereEqualTo("isPublic", true)
                    .get()
                    .await()
                    
                val fetchedLists = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(GameList::class.java)
                }
                
                withContext(Dispatchers.Main) {
                    lists.clear()
                    lists.addAll(fetchedLists)
                    listsAdapter.submitList(lists.toList())
                    showEmptyView(lists.isEmpty())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading public lists", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_loading_lists, Toast.LENGTH_SHORT).show()
                    showEmptyView(true)
                }
            }
        }
    }
    
    private fun showEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = if (showPublicLists) {
                getString(R.string.lists_no_public_lists)
            } else {
                getString(R.string.lists_no_games)
            }
        } else {
            emptyView.visibility = View.GONE
        }
    }
}