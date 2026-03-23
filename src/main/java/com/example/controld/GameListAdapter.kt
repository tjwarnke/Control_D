package com.example.controld

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.controld.com.example.controld.GameListItem
import java.text.SimpleDateFormat
import java.util.Locale

class GameListAdapter(
    private val onItemClick: (GameListItem) -> Unit
) : ListAdapter<GameListItem, GameListAdapter.GameListItemViewHolder>(GameListItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameListItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_game, parent, false)
        return GameListItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameListItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GameListItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gameTitle: TextView = itemView.findViewById(R.id.game_title)
        private val gameImage: ImageView = itemView.findViewById(R.id.game_image)
        private val dateAdded: TextView? = itemView.findViewById(R.id.added_date)
        private val notes: TextView = itemView.findViewById(R.id.game_note)

        fun bind(item: GameListItem) {
            gameTitle.text = item.gameTitle
            
            // Load game image
            Glide.with(itemView.context)
                .load(item.gameCoverUrl)
                .placeholder(R.drawable.placeholder_game_cover)
                .into(gameImage)
            
            // Format date if the view exists
            dateAdded?.let {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                it.text = itemView.context.getString(
                    R.string.lists_added_on, 
                    dateFormat.format(item.addedAt)
                )
            }
            
            // Set up notes
            if (item.notes.isBlank()) {
                notes.visibility = View.GONE
            } else {
                notes.visibility = View.VISIBLE
                notes.text = item.notes
            }
            
            // Set click listener
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    private class GameListItemDiffCallback : DiffUtil.ItemCallback<GameListItem>() {
        override fun areItemsTheSame(oldItem: GameListItem, newItem: GameListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GameListItem, newItem: GameListItem): Boolean {
            return oldItem == newItem
        }
    }
}