package com.black.app.ui.maintab.main

import android.os.Bundle
import androidx.navigation.NavDirections
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.app.R
import com.black.app.databinding.FragmentMainBinding
import com.black.app.deeplink.DeeplinkManager
import com.black.app.ui.maintab.MainTabFragmentDirections
import com.black.core.component.BaseFragment
import com.black.core.util.FragmentExtension.navigate
import com.black.feature.pokerogue.ui.PokeRogueFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseFragment<FragmentMainBinding>() {

    // static list이고, 리소스 디펜던시가 있기 때문에 View에서 관리
    private val itemList by lazy {
        listOf(
            /** [PokeRogueFragment] */
            MainItem(
                getString(com.black.feature.pokerogue.R.string.poke_rogue),
                com.black.feature.pokerogue.R.drawable.ic_poke_rogue,
            ) { navigateByActivity(MainTabFragmentDirections.actionPokeRogue()) },

            MainItem(
                getString(R.string.fragment_name_text_editor),
                R.drawable.ic_editor,
            ) { navigateByActivity(MainTabFragmentDirections.actionTextEditor()) },
            MainItem(
                getString(R.string.fragment_name_black),
                R.drawable.ic_invisible,
            ) { navigateByActivity(MainTabFragmentDirections.actionBlack()) },
            MainItem(
                getString(R.string.fragment_name_service),
                R.drawable.ic_android,
            ) { navigateByActivity(MainTabFragmentDirections.actionService()) },
            MainItem(
                getString(R.string.fragment_name_study_popup),
                R.drawable.ic_quiz,
            ) { navigateByActivity(MainTabFragmentDirections.actionStudyPopup()) },
            MainItem(
                getString(R.string.fragment_name_usage_timer),
                R.drawable.ic_timer,
            ) { navigateByActivity(MainTabFragmentDirections.actionUsageTimer()) },
            MainItem(
                getString(R.string.fragment_name_notification),
                R.drawable.ic_notification,
            ) { navigateByActivity(MainTabFragmentDirections.actionNotification()) },
            MainItem(
                getString(R.string.fragment_name_recycler_view),
                R.drawable.ic_list,
            ) { navigateByActivity(MainTabFragmentDirections.actionRecyclerView()) },
            MainItem(
                getString(R.string.fragment_name_retrofit),
                R.drawable.ic_http,
            ) { navigateByActivity(MainTabFragmentDirections.actionRetrofit()) },
            MainItem(
                getString(R.string.fragment_name_alarm),
                R.drawable.ic_alarm,
            ) { navigateByActivity(MainTabFragmentDirections.actionAlarm()) },
            MainItem(
                getString(R.string.fragment_name_architecture),
                R.drawable.ic_architecture,
            ) { navigateByActivity(MainTabFragmentDirections.actionArchitecture()) },
            MainItem(
                getString(R.string.fragment_name_launcher),
                R.drawable.ic_home,
            ) { navigateByActivity(MainTabFragmentDirections.actionLauncher()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },

            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(MainTabFragmentDirections.actionEtc()) },
        )
    }

    @Inject
    lateinit var deeplinkManager: DeeplinkManager

    private lateinit var adapter: MainGridAdapter

    override val layoutResId: Int = R.layout.fragment_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = MainGridAdapter()
    }

    override fun onBindVariable(binding: FragmentMainBinding) {
        // ViewModel 생략
        binding.recyclerView.apply {
            adapter = this@MainFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        adapter.submitList(itemList)
    }

    private fun navigateByActivity(directions: NavDirections) {
        navigate(directions, requireActivity().findNavController(R.id.root_nav_host))
    }
}