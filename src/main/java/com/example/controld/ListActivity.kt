package com.example.controld

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.com.example.controld.GameList
import com.example.controld.com.example.controld.GameListItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

open class ListActivity : AppCompatActivity() {
    protected lateinit var adapter: ActivityAdapter
    protected var currentSortType: SortType = SortType.NEWEST
    private lateinit var gameListAdapter: GameListAdapter
    private lateinit var listNameTextView: TextView
    private lateinit var listDescriptionTextView: TextView
    private lateinit var emptyView: TextView
    private var currentList: GameList? = null
    private val firestore = Firebase.firestore
    private var userId: String = ""
    private var isUserOwner: Boolean = false

    open fun getTitleString(): String = ""

    open fun loadData() {}

    companion object {
        private const val TAG = "ListActivity"
        private const val EXTRA_LIST_ID = "extra_list_id"

        fun newIntent(context: Context, listId: String): Intent {
            return Intent(context, ListActivity::class.java).apply {
                putExtra(EXTRA_LIST_ID, listId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        // Get current user ID
        val sharedPref = getSharedPreferences(getString(R.string.accountPrefKey), MODE_PRIVATE)
        userId = sharedPref.getString(getString(R.string.emailKey), "") ?: ""

        // Get list ID from intent
        val listId = intent.getStringExtra(EXTRA_LIST_ID)
        if (listId.isNullOrEmpty()) {
            finish()
            return
        }

        // Initialize views
        listNameTextView = findViewById(R.id.list_name)
        listDescriptionTextView = findViewById(R.id.list_description)
        val gamesRecyclerView = findViewById<RecyclerView>(R.id.games_recycler_view)
        val addGameFab = findViewById<FloatingActionButton>(R.id.add_game_fab)
        emptyView = findViewById(R.id.empty_view)

        // Set up back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up RecyclerView
        gameListAdapter = GameListAdapter { gameItem ->
            showEditGameDialog(gameItem)
        }
        gamesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ListActivity)
            adapter = gameListAdapter
        }

        // Set up FAB - initially hidden until we confirm ownership
        addGameFab.visibility = View.GONE
        addGameFab.setOnClickListener {
            navigateToSearch()
        }

        // Load list data
        loadList(listId)
    }

    private fun loadList(listId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docSnapshot = firestore.collection("lists").document(listId).get().await()
                val list = docSnapshot.toObject(GameList::class.java)
                
                withContext(Dispatchers.Main) {
                    if (list != null) {
                        currentList = list
                        updateListDisplay()
                        
                        // Check if current user is the owner
                        isUserOwner = list.userId == userId
                        invalidateOptionsMenu() // Update menu options
                        
                        // Show/hide the FAB based on ownership
                        findViewById<FloatingActionButton>(R.id.add_game_fab).visibility = 
                            if (isUserOwner) View.VISIBLE else View.GONE
                    } else {
                        Toast.makeText(this@ListActivity, R.string.error_loading_list, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading list", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListActivity, R.string.error_loading_list, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun updateListDisplay() {
        // Update title and description
        supportActionBar?.title = currentList?.name ?: getString(R.string.app_name)
        
        // Show empty state or games list
        currentList?.let { list ->
            if (list.items.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                findViewById<RecyclerView>(R.id.games_recycler_view).visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                findViewById<RecyclerView>(R.id.games_recycler_view).visibility = View.VISIBLE
                gameListAdapter.submitList(list.items)
            }
        }
    }

    private fun navigateToSearch() {
        // Navigate to search with context that we're adding to a list
        val listId = currentList?.id ?: return
        
        // Instead of trying to start a SearchActivity, we'll go back to MainActivity
        // and the MainActivity will navigate to the search tab
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_search", true)
            putExtra("list_id", listId)
            putExtra("add_to_list_mode", true)
        }
        startActivity(intent)
        
        // Show a toast to guide the user
        Toast.makeText(
            this, 
            "Search for games to add to your list", 
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showEditGameDialog(gameItem: GameListItem) {
        // Only allow editing for the list owner
        if (!isUserOwner) {
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_game_list_item, null)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.notes_input)
        notesInput.setText(gameItem.notes)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.lists_edit_game_notes, gameItem.gameTitle))
            .setView(dialogView)
            .setPositiveButton(R.string.lists_save) { dialog, _ ->
                updateGameNotes(gameItem, notesInput.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.lists_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(R.string.lists_remove_game) { dialog, _ ->
                showDeleteConfirmationDialog(gameItem)
                dialog.dismiss()
            }
            .show()
    }

    private fun updateGameNotes(gameItem: GameListItem, notes: String) {
        currentList?.let { list ->
            // Find game in the list and update notes
            val updatedItems = list.items.map { 
                if (it.id == gameItem.id) it.copy(notes = notes) else it 
            }
            
            // Update in Firestore
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore.collection("lists").document(list.id)
                        .update(
                            mapOf(
                                "items" to updatedItems,
                                "updatedAt" to Date()
                            )
                        )
                        .await()
                    
                    // Update local model
                    withContext(Dispatchers.Main) {
                        currentList = list.copy(items = updatedItems, updatedAt = Date())
                        updateListDisplay()
                        Toast.makeText(this@ListActivity, R.string.lists_updated, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating game notes", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListActivity, R.string.error_updating_list, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(gameItem: GameListItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lists_remove_game)
            .setMessage(getString(R.string.lists_confirm_remove_game, gameItem.gameTitle))
            .setPositiveButton(R.string.lists_remove) { dialog, _ ->
                removeGameFromList(gameItem)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.lists_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun removeGameFromList(gameItem: GameListItem) {
        currentList?.let { list ->
            // Remove game from the list
            val updatedItems = list.items.filter { it.id != gameItem.id }
            
            // Update in Firestore
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore.collection("lists").document(list.id)
                        .update(
                            mapOf(
                                "items" to updatedItems,
                                "updatedAt" to Date()
                            )
                        )
                        .await()
                    
                    // Update local model
                    withContext(Dispatchers.Main) {
                        currentList = list.copy(items = updatedItems, updatedAt = Date())
                        updateListDisplay()
                        Toast.makeText(this@ListActivity, R.string.lists_game_removed, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing game from list", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListActivity, R.string.error_updating_list, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Only show edit options for the list owner
        if (isUserOwner) {
            menuInflater.inflate(R.menu.menu_list, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_edit_list -> {
                showEditListDialog()
                true
            }
            R.id.action_delete_list -> {
                showDeleteListDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEditListDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_list, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.list_name_input)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.list_description_input)
        val isPublicSwitch = dialogView.findViewById<SwitchMaterial>(R.id.public_switch)

        currentList?.let { list ->
            nameInput.setText(list.name)
            descriptionInput.setText(list.description)
            isPublicSwitch.isChecked = list.isPublic
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lists_edit_list)
            .setView(dialogView)
            .setPositiveButton(R.string.lists_save) { dialog, _ ->
                val name = nameInput.text.toString()
                val description = descriptionInput.text.toString()
                val isPublic = isPublicSwitch.isChecked

                if (name.isNotBlank()) {
                    updateList(name, description, isPublic)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.lists_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.lists_cancel, null)
            .show()
    }

    private fun updateList(name: String, description: String, isPublic: Boolean) {
        currentList?.let { list ->
            // Update in Firestore
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore.collection("lists").document(list.id)
                        .update(
                            mapOf(
                                "name" to name,
                                "description" to description,
                                "isPublic" to isPublic,
                                "updatedAt" to Date()
                            )
                        )
                        .await()
                    
                    // Update local model
                    withContext(Dispatchers.Main) {
                        currentList = list.copy(
                            name = name,
                            description = description,
                            isPublic = isPublic,
                            updatedAt = Date()
                        )
                        updateListDisplay()
                        Toast.makeText(this@ListActivity, R.string.lists_updated, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating list", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListActivity, R.string.error_updating_list, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showDeleteListDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lists_delete_list)
            .setMessage(R.string.lists_confirm_delete_list)
            .setPositiveButton(R.string.lists_delete) { dialog, _ ->
                deleteList()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.lists_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteList() {
        currentList?.let { list ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore.collection("lists").document(list.id)
                        .delete()
                        .await()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListActivity, R.string.lists_deleted, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting list", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListActivity, R.string.error_deleting_list, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}