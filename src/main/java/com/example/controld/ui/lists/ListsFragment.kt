package com.example.controld.ui.lists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controld.ListActivity
import com.example.controld.ListsAdapter
import com.example.controld.R
import com.example.controld.com.example.controld.GameList
import com.example.controld.com.example.controld.GameListItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ListsFragment : Fragment() {
    private lateinit var adapter: ListsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addListFab: FloatingActionButton
    private lateinit var emptyView: View
    private val firestore = Firebase.firestore
    private var userId: String = ""

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
        val sharedPref = requireActivity().getSharedPreferences(
            getString(R.string.accountPrefKey), 
            android.content.Context.MODE_PRIVATE
        )
        userId = sharedPref.getString(getString(R.string.emailKey), "") ?: ""

        // Initialize views
        recyclerView = view.findViewById(R.id.lists_recycler_view)
        addListFab = view.findViewById(R.id.create_list_fab)
        emptyView = view.findViewById(R.id.empty_view)

        // Set up RecyclerView
        adapter = ListsAdapter { list ->
            // Handle list click - navigate to ListActivity
            val intent = ListActivity.newIntent(requireContext(), list.id)
            startActivity(intent)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ListsFragment.adapter
        }

        // Set up FAB
        addListFab.setOnClickListener {
            showCreateListDialog()
        }

        // Load lists
        loadLists()
    }

    private fun loadLists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val querySnapshot = firestore.collection("lists")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                
                val lists = querySnapshot.toObjects(GameList::class.java)
                
                withContext(Dispatchers.Main) {
                    if (lists.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.submitList(lists)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.error_loading_lists), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showCreateListDialog() {
        // Implementation for creating a new list
        // This would show a dialog with fields for name, description, etc.
        // For now, just show a toast message
        Toast.makeText(requireContext(), "Create list functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun addGameToList(game: GameListItem, listId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val listDoc = firestore.collection("lists").document(listId).get().await()
                val list = listDoc.toObject(GameList::class.java) ?: return@launch
                
                // Check if game already exists in the list
                if (list.items.any { it.id == game.id }) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.game_already_in_list), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Add game to list
                val updatedItems = list.items.toMutableList()
                updatedItems.add(game)
                
                firestore.collection("lists").document(listId)
                    .update("items", updatedItems)
                    .await()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.game_added_to_list), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.error_adding_game), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
} 