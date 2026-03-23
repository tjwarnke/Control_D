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
import com.example.controld.com.example.controld.GameList
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale

class ListsAdapter(
    private val onListClick: (GameList) -> Unit
) : ListAdapter<GameList, ListsAdapter.ListViewHolder>(ListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_list, parent, false)
        return ListViewHolder(view)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val listName: TextView = itemView.findViewById(R.id.list_name)
        private val listDescription: TextView = itemView.findViewById(R.id.list_description)
        private val gameCount: TextView = itemView.findViewById(R.id.game_count)
        private val createdAt: TextView = itemView.findViewById(R.id.created_at)
        private val listCoverImage: ImageView = itemView.findViewById(R.id.list_cover_image)
        
        fun bind(list: GameList) {
            listName.text = list.name
            listDescription.text = list.description
            gameCount.text = itemView.context.getString(
                R.string.lists_game_count,
                list.items.size,
                if (list.items.size == 1) "" else "s"
            )

            // Format and display creation date
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            createdAt.text = itemView.context.getString(R.string.lists_created_at, dateFormat.format(list.createdAt))
            
            // Icon for public/private is handled by a background drawable instead
            
            // Load cover image
            if (list.items.isNotEmpty()) {
                // Use the first game's cover image
                val coverGame = list.items.firstOrNull()
                if (coverGame?.gameCoverUrl?.isNotEmpty() == true) {
                    Glide.with(itemView.context)
                        .load(coverGame.gameCoverUrl)
                        .placeholder(R.drawable.placeholder_game_cover)
                        .into(listCoverImage)
                }
            } else {
                listCoverImage.setImageResource(R.drawable.placeholder_game_cover)
            }

            // Set up click listener
            itemView.setOnClickListener { onListClick(list) }
        }
    }

    private class ListDiffCallback : DiffUtil.ItemCallback<GameList>() {
        override fun areItemsTheSame(oldItem: GameList, newItem: GameList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GameList, newItem: GameList): Boolean {
            return oldItem == newItem
        }
    }
}