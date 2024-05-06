package com.black.app.ui

import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.app.R
import com.black.app.databinding.FragmentMainBinding
import com.black.core.viewmodel.EventObserver

class MainFragment
    : com.black.core.component.BaseFragment<FragmentMainBinding>(),
    EventObserver {

    // static list이고, 리소스 디펜던시가 있기 때문에 View에서 관리
    private val itemList by lazy {
        listOf(
            MainItem(
                getString(R.string.fragment_name_text_editor),
                R.drawable.ic_editor,
            ) { MainFragmentDirections.actionTextEditor() },
            MainItem(
                getString(R.string.fragment_name_black),
                R.drawable.ic_invisible,
            ) { MainFragmentDirections.actionBlack() },
            MainItem(
                getString(R.string.fragment_name_service),
                R.drawable.ic_android,
            ) { MainFragmentDirections.actionService() },
            MainItem(
                getString(R.string.fragment_name_study_popup),
                R.drawable.ic_quiz,
            ) { MainFragmentDirections.actionStudyPopup() },
            MainItem(
                getString(R.string.fragment_name_usage_timer),
                R.drawable.ic_timer,
            ) { MainFragmentDirections.actionUsageTimer() },
            MainItem(
                getString(R.string.fragment_name_notification),
                R.drawable.ic_notification,
            ) { MainFragmentDirections.actionNotification() },
            MainItem(
                getString(R.string.fragment_name_recycler_view),
                R.drawable.ic_list,
            ) { MainFragmentDirections.actionRecyclerView() },
            MainItem(
                getString(R.string.fragment_name_retrofit),
                R.drawable.ic_http,
            ) { MainFragmentDirections.actionRetrofit() },
            MainItem(
                getString(R.string.fragment_name_alarm),
                R.drawable.ic_alarm,
            ) { MainFragmentDirections.actionAlarm() },
            MainItem(
                getString(R.string.fragment_name_architecture),
                R.drawable.ic_architecture,
            ) { MainFragmentDirections.actionArchitecture() },
            MainItem(
                getString(R.string.fragment_name_launcher),
                R.drawable.ic_home,
            ) { MainFragmentDirections.actionLauncher() },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { MainFragmentDirections.actionEtc() }
        )
    }

    private val adapter by lazy { MainGridAdapter() }
    private val navController by lazy { findNavController() }

    override val layoutResId: Int = R.layout.fragment_main

    override fun bindVariable(binding: FragmentMainBinding) {
        // ViewModel 생략
        binding.recyclerView.apply {
            adapter = this@MainFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        adapter.submitList(itemList)
    }

    override fun onReceivedEvent(action: String, data: Any?) { }
}