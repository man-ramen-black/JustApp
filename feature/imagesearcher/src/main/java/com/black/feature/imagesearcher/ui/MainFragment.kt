package com.black.feature.imagesearcher.ui

import android.os.Bundle
import android.view.View
import com.black.core.component.BaseFragment
import com.black.core.util.UiUtil.setupWithViewPager2
import com.black.feature.imagesearcher.R
import com.black.feature.imagesearcher.databinding.ImageSearcherFragmentMainBinding
import com.black.feature.imagesearcher.ui.search.SearchFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment: BaseFragment<ImageSearcherFragmentMainBinding>() {

    private val tabs by lazy {
        listOf(
            MainTab(
                getString(R.string.main_tab_search)
            ) { SearchFragment() },
//            MainTab(
//                getString(R.string.main_tab_favorite)
//            ) { FavoriteFragment() }
        )
    }
    private lateinit var adapter: MainTabAdapter

    override val layoutResId: Int = R.layout.image_searcher_fragment_main

    override fun onBindVariable(binding: ImageSearcherFragmentMainBinding) {
        adapter = MainTabAdapter(this, tabs)
        binding.adapter = adapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.executePendingBindings()
        binding.tabLayout.setupWithViewPager2(
            viewPager = binding.viewPager,
            smoothScroll = false,
            tabText = { _, position -> tabs[position].title }
        )
    }
}