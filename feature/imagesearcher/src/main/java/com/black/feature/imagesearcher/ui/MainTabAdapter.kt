package com.black.feature.imagesearcher.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * [MainFragment]
 */
class MainTab(val title: String, val createFragment: () -> Fragment)

class MainTabAdapter(fragment: Fragment, private val tabs: List<MainTab>): FragmentStateAdapter(fragment) {
    override fun createFragment(position: Int): Fragment {
        return tabs[position].createFragment()
    }

    override fun getItemCount(): Int {
        return tabs.size
    }
}