package com.example.controld

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class RecentSearchAdapter(
    private val onSearchClick: (String) -> Unit,
    private val onClearClick: (String) -> Unit
) : ListAdapter<String, RecentSearchAdapter.RecentSearchViewHolder>(RecentSearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_search, parent, false)
        return RecentSearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentSearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val searchText: TextView = itemView.findViewById(R.id.searchText)
        private val clearButton: ImageButton = itemView.findViewById(R.id.clearButton)

        fun bind(query: String) {
            searchText.text = query
            searchText.setOnClickListener { onSearchClick(query) }
            clearButton.setOnClickListener { onClearClick(query) }
        }
    }

    private class RecentSearchDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
} 