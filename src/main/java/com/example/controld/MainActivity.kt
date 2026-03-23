package com.example.controld

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

const val OVERRIDEACCOUNTPREF = true //true changes saved account information
const val EMAIL =  "stensdr@gmail.com" //"null" to reset saved userID
const val PASSWORD = "6eca3df4e82a5b09bc676ac26a6cf394de021768e23fc24bcafc6eee4e233d18" //"null" to reset saved password

class MainActivity : AppCompatActivity() {
    
    private lateinit var bottomNavigationView: BottomNavigationView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main) // Load XML-based UI

        FirebaseFirestore.setLoggingEnabled(true)
        val firestore = Firebase.firestore

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Check if we should navigate to search tab
        val openSearch = intent.getBooleanExtra("open_search", false)
        val listId = intent.getStringExtra("list_id")
        val addToListMode = intent.getBooleanExtra("add_to_list_mode", false)
        
        // Load the default fragment or search fragment if requested
        if (openSearch) {
            val searchFragment = SearchFragment().apply {
                arguments = Bundle().apply {
                    putString("list_id", listId)
                    putBoolean("add_to_list_mode", addToListMode)
                }
            }
            loadFragment(searchFragment)
            bottomNavigationView.selectedItemId = R.id.nav_search
        } else {
            loadFragment(HomeFragment())
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_search -> SearchFragment()
                R.id.nav_profile -> AccountFragment() // Using AccountFragment instead of ProfileFragment
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(selectedFragment)
            true
        }

        //Set up shared preference
        val sharedPref = applicationContext.getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
        val userID = sharedPref.getString(getString(R.string.emailKey),"null") ?: "null"
        val password = sharedPref.getString(getString(R.string.passwordKey),"null") ?: "null"
        if(userID == "null" || password == "null"){ //One value is null
            sharedPref.edit()
                .putString(getString(R.string.emailKey), "null")
                .putString(getString(R.string.passwordKey),"null")
                .apply()
        }else{  //Check if account exists and password is correct
            firestore.collection("users")
                .document(userID) //Filter using email
                .get() //retrieve results
                .addOnSuccessListener { user ->
                    if(user == null || password != user.get("password")){
                        Log.d("tag", "Login unsuccessful")
                        sharedPref.edit()
                            .putString(getString(R.string.emailKey), "null")
                            .putString(getString(R.string.passwordKey),"null")
                            .apply()
                    }else{
                        Log.d("tag", "Login successful")
                    }
                }
        }

        //only use for debug
        if(OVERRIDEACCOUNTPREF){
            sharedPref.edit()
                .putString(getString(R.string.emailKey), EMAIL)
                .putString(getString(R.string.passwordKey), PASSWORD)
                .apply()
        }

        //Account info
        Log.d("TAG","U: "+
                "${sharedPref.getString(getString(R.string.emailKey),"null")}; "+
                "P: ${sharedPref.getString(getString(R.string.passwordKey),"null")}"
        )
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

