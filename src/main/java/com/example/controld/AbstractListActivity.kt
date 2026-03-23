package com.example.controld

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

abstract class AbstractListActivity : AppCompatActivity(), ActivityAdapter.Callbacks {
    protected lateinit var adapter: ActivityAdapter
    protected var currentSortType: SortType = SortType.NEWEST
    
    abstract fun getTitleString(): String
    
    abstract fun loadData()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_general_list)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getTitleString()
        
        adapter = ActivityAdapter(this, this)
        
        val recyclerView = findViewById<RecyclerView>(R.id.general_list_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        loadData()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sort, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc)
        )
        
        val checkedItem = when (currentSortType) {
            SortType.NEWEST -> 0
            SortType.OLDEST -> 1
            SortType.NAME_ASC -> 2
            SortType.NAME_DESC -> 3
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sort))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentSortType = when (which) {
                    0 -> SortType.NEWEST
                    1 -> SortType.OLDEST
                    2 -> SortType.NAME_ASC
                    3 -> SortType.NAME_DESC
                    else -> SortType.NEWEST
                }
                loadData()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.lists_cancel), null)
            .show()
    }
    
    // Implement required method from ActivityAdapter.Callbacks
    override fun handleUserData(dataType: String, data: ActivityItem) {
        // Default implementation does nothing, can be overridden by subclasses if needed
    }
} 