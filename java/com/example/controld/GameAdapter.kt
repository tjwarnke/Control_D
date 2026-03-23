package com.example.controld

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GameAdapter(val context: Context?, val handler: GameAdapter.Callbacks) : ListAdapter<Game, GameAdapter.ViewHolder>(GameDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private lateinit var gameID: String
        private val gameImage: ImageView = itemView.findViewById(R.id.gameImage)

        fun bind(game: Game) {
            gameID = game.id


            itemView.setOnClickListener {
                handler.handleGamePress(game.id)
            }

            Glide.with(itemView.context)
                .load(game.imageUrl)
                .placeholder(R.drawable.placeholder_game_cover)
                .error(R.drawable.error_game_cover)
                .into(gameImage)
        }


    }

    private class GameDiffCallback : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean {
            return oldItem == newItem
        }
    }

    interface Callbacks {
        fun handleGamePress(id: String)
    }
}