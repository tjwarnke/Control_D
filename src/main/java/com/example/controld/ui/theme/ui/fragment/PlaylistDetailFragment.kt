package com.example.controld.ui.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.controld.R
import com.example.controld.data.model.Playlist
import com.example.controld.data.model.PlaylistItem
import com.example.controld.data.model.PlaylistSortOrder
import com.example.controld.data.model.PlaylistSortType
import com.example.controld.ui.adapter.PlaylistItemAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

enum class SortOption {
    DATE, NAME
}

class PlaylistDetailFragment : Fragment() {

    private var playlist: Playlist? = null
    private lateinit var adapter: PlaylistItemAdapter
    private var currentSortType = PlaylistSortType.ADDED_DATE
    private var currentSortOrder = PlaylistSortOrder.DESCENDING

    private lateinit var coverImage: ImageView
    private lateinit var nameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var itemCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var fab: FloatingActionButton
    private lateinit var collapsingToolbar: CollapsingToolbarLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        coverImage = view.findViewById(R.id.playlist_cover)
        nameText = view.findViewById(R.id.playlist_name)
        descriptionText = view.findViewById(R.id.playlist_description)
        itemCountText = view.findViewById(R.id.playlist_item_count)
        recyclerView = view.findViewById(R.id.playlist_items)
        toolbar = view.findViewById(R.id.toolbar)
        fab = view.findViewById(R.id.fab_add_items)
        collapsingToolbar = view.findViewById(R.id.collapsing_toolbar)

        // Setup toolbar
        toolbar.setNavigationOnClickListener { 
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                R.id.action_edit -> {
                    showEditDialog()
                    true
                }
                R.id.action_delete -> {
                    showDeleteDialog()
                    true
                }
                else -> false
            }
        }

        // Setup recycler view
        adapter = PlaylistItemAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlaylistDetailFragment.adapter
        }

        // Setup FAB
        fab.setOnClickListener { showAddItemsDialog() }

        // Load playlist data
        arguments?.getString(ARG_PLAYLIST_ID)?.let { playlistId ->
            loadPlaylist(playlistId)
        }
    }

    private fun loadPlaylist(playlistId: String) {
        // TODO: Load playlist data from repository
        // For now, use sample data
        playlist = Playlist(
            id = playlistId,
            name = "Sample Playlist",
            description = "This is a sample playlist",
            coverUrl = null,
            itemCount = 2,
            createdAt = java.util.Date(),
            updatedAt = java.util.Date()
        )

        updateUI()
    }

    private fun updateUI() {
        playlist?.let { playlist ->
            nameText.text = playlist.name
            descriptionText.text = playlist.description
            itemCountText.text = getString(R.string.playlist_item_count, playlist.itemCount)

            Glide.with(coverImage)
                .load(playlist.coverUrl)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .into(coverImage)

            // Load sample items
            val sampleItems = listOf(
                PlaylistItem(
                    id = "1",
                    playlistId = playlist.id,
                    title = "The Legend of Zelda: Breath of the Wild",
                    subtitle = "Nintendo Switch",
                    coverUrl = "https://example.com/botw.jpg",
                    addedAt = java.util.Date(),
                    position = 1
                ),
                PlaylistItem(
                    id = "2",
                    playlistId = playlist.id,
                    title = "Elden Ring",
                    subtitle = "FromSoftware",
                    coverUrl = "https://example.com/elden-ring.jpg",
                    addedAt = java.util.Date(),
                    position = 2
                )
            )
            adapter.submitList(sampleItems)

            collapsingToolbar.title = playlist.name
            toolbar.title = playlist.name
        }
    }

    private fun showSortDialog() {
        val items = arrayOf(
            getString(R.string.sort_by_date),
            getString(R.string.sort_by_name)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sort)
            .setItems(items) { dialog: DialogInterface, which: Int ->
                when (which) {
                    0 -> sortPlaylistItems(SortOption.DATE)
                    1 -> sortPlaylistItems(SortOption.NAME)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.playlist_cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_playlist, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_description_input)

        nameInput.setText(playlist?.name ?: "")
        descriptionInput.setText(playlist?.description ?: "")

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.playlist_edit)
            .setView(dialogView)
            .setPositiveButton(R.string.playlist_save) { dialog: DialogInterface, _: Int ->
                val name = nameInput.text.toString()
                val description = descriptionInput.text.toString()
                if (name.isNotBlank()) {
                    playlist?.let { currentPlaylist ->
                        // Create a new playlist instance with updated values
                        playlist = currentPlaylist.copy(
                            name = name,
                            description = description
                        )
                        updateUI()
                        // TODO: Update playlist in ViewModel
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.playlist_cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.playlist_delete)
            .setMessage(R.string.playlist_confirm_delete)
            .setPositiveButton(R.string.delete) { dialog: DialogInterface, _: Int ->
                playlist?.let {
                    // TODO: Delete playlist in ViewModel
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.playlist_cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAddItemsDialog() {
        // TODO: Implement add items dialog
        Snackbar.make(requireView(), "Add items functionality coming soon", Snackbar.LENGTH_SHORT).show()
    }

    private fun sortPlaylistItems(option: SortOption) {
        // TODO: Implement sorting
        val currentItems = adapter.currentList.toMutableList()
        val sortedItems = when (option) {
            SortOption.DATE -> currentItems.sortedBy { it.addedAt }
            SortOption.NAME -> currentItems.sortedBy { it.title }
        }
        adapter.submitList(sortedItems)
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"

        fun newInstance(playlistId: String): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                }
            }
        }
    }
}