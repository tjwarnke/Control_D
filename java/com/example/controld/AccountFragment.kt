package com.example.controld

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.adapter.FragmentStateAdapter

class AccountFragment : Fragment() {
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        container?.removeAllViews()
        // Use fragment_account.xml instead of fragment_profile.xml
        return inflater.inflate(R.layout.fragment_account, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Check for existing profile
        val sharedPref = requireContext().getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
        val userID = sharedPref.getString(getString(R.string.emailKey),"null") ?: "null"
        if(userID == "null"){
            loadFragment(SignInFragment())
        }

        viewPager = view.findViewById(R.id.account_view_pager)
        tabLayout = view.findViewById(R.id.account_tabs)

        // Set up the ViewPager with the sections adapter
        viewPager.adapter = AccountPagerAdapter(this)

        // Enable user input for swiping
        viewPager.isUserInputEnabled = true

        // Connect the TabLayout with the ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Profile"
                1 -> "Lists"
                else -> null
            }
        }.attach()
    }

     fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
        }
    }
}

class AccountPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        // Return a new fragment instance for each position
        return when (position) {
            0 -> ProfileFragment()
            1 -> ListsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
