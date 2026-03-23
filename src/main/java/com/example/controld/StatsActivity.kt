package com.example.controld

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.controld.ui.stats.StatsFragment

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.stats_container, StatsFragment())
                .commit()
        }
    }
} 