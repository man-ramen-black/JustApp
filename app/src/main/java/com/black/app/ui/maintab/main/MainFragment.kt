package com.black.app.ui.maintab.main

import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.app.R
import com.black.app.databinding.FragmentMainBinding
import com.black.app.deeplink.DeeplinkManager
import com.black.app.ui.navigation.AppNavigator
import com.black.app.ui.navigation.AppRoute
import com.black.core.component.BaseFragment
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
            ) { navigateByActivity(AppRoute.PokeRogue) },

            MainItem(
                getString(R.string.fragment_name_text_editor),
                R.drawable.ic_editor,
            ) { navigateByActivity(AppRoute.TextEditor) },
            MainItem(
                getString(R.string.fragment_name_black),
                R.drawable.ic_invisible,
            ) { navigateByActivity(AppRoute.Black) },
            MainItem(
                getString(R.string.fragment_name_floating),
                R.drawable.ic_floating,
            ) { navigateByActivity(AppRoute.Floating) },
            MainItem(
                getString(R.string.fragment_name_service),
                R.drawable.ic_android,
            ) { navigateByActivity(AppRoute.Service) },
            MainItem(
                getString(R.string.fragment_name_study_popup),
                R.drawable.ic_quiz,
            ) { navigateByActivity(AppRoute.StudyPopup) },
            MainItem(
                getString(R.string.fragment_name_usage_timer),
                R.drawable.ic_timer,
            ) { navigateByActivity(AppRoute.UsageTimer) },
            MainItem(
                getString(R.string.fragment_name_notification),
                R.drawable.ic_notify,
            ) { navigateByActivity(AppRoute.Notification) },
            MainItem(
                getString(R.string.fragment_name_recycler_view),
                R.drawable.ic_list,
            ) { navigateByActivity(AppRoute.RecyclerView) },
            MainItem(
                getString(R.string.fragment_name_retrofit),
                R.drawable.ic_http,
            ) { navigateByActivity(AppRoute.Retrofit) },
            MainItem(
                getString(R.string.fragment_name_alarm),
                R.drawable.ic_alarm,
            ) { navigateByActivity(AppRoute.Alarm) },
            MainItem(
                getString(R.string.fragment_name_architecture),
                R.drawable.ic_architecture,
            ) { navigateByActivity(AppRoute.Architecture) },
            MainItem(
                getString(R.string.fragment_name_launcher),
                R.drawable.ic_home,
            ) { navigateByActivity(AppRoute.Launcher) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },

            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
            MainItem(
                getString(R.string.fragment_name_etc),
                R.drawable.ic_etc
            ) { navigateByActivity(AppRoute.Etc) },
        )
    }

    @Inject
    lateinit var deeplinkManager: DeeplinkManager

    @Inject
    lateinit var appNavigator: AppNavigator

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

    /** 루트 Compose NavController를 통한 화면 이동 */
    private fun navigateByActivity(route: AppRoute) {
        appNavigator.navigateSafely(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}
