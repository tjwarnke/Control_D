package com.example.controld

import android.content.Context
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

class ActivityAdapter(val context: Context?, val handler: ActivityAdapter.Callbacks) : ListAdapter<ActivityItem, ActivityAdapter.ViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        private val typeTextView: TextView = itemView.findViewById(R.id.type_text_view)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)

        fun bind(item: ActivityItem) {
            nameTextView.text = item.name
            titleTextView.text = item.gameTitle
            typeTextView.text = item.type

            itemView.setOnClickListener {
                Log.d("ActivityAdapter", item.name)
                Log.d("ActivityAdapter", item.toString())
                handler.handleUserData(item.gameTitle, item)
            }

            Glide.with(itemView.context)
                .load(item.avatarUrl)
                .placeholder(R.drawable.placeholder_game_cover)
                .error(R.drawable.error_game_cover)
                .circleCrop()
                .into(avatarImageView)
        }


    }

    private class ActivityDiffCallback : DiffUtil.ItemCallback<ActivityItem>() {
        override fun areItemsTheSame(oldItem: ActivityItem, newItem: ActivityItem): Boolean {
            return oldItem.name == newItem.name && oldItem.avatarUrl == newItem.avatarUrl
        }

        override fun areContentsTheSame(oldItem: ActivityItem, newItem: ActivityItem): Boolean {
            return oldItem == newItem
        }
    }

    interface Callbacks {
        fun handleUserData(dataType: String, id: ActivityItem)
    }
}