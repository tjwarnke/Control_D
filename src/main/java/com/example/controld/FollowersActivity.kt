package com.example.controld

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.controld.ui.followers.FollowersFragment

class FollowersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.followers_container, FollowersFragment())
                .commit()
        }
    }
}