package com.example.controld

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class PopularSearchAdapter(
    private val onSearchClick: (String) -> Unit
) : ListAdapter<String, PopularSearchAdapter.PopularSearchViewHolder>(PopularSearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PopularSearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_popular_search, parent, false)
        return PopularSearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: PopularSearchViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class PopularSearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankText: TextView = itemView.findViewById(R.id.rankText)
        private val searchText: TextView = itemView.findViewById(R.id.searchText)

        fun bind(query: String, rank: Int) {
            rankText.text = rank.toString()
            searchText.text = query
            itemView.setOnClickListener { onSearchClick(query) }
        }
    }

    private class PopularSearchDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
} 