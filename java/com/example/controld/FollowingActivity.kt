package com.example.controld

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.controld.ui.following.FollowingFragment

class FollowingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.following_container, FollowingFragment())
                .commit()
        }
    }
}