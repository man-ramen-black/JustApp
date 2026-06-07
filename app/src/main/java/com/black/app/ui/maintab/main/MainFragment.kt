package com.black.app.ui.maintab.main

import androidx.compose.ui.platform.ViewCompositionStrategy
import com.black.app.R
import com.black.app.databinding.FragmentMainBinding
import com.black.app.deeplink.DeeplinkManager
import com.black.app.ui.navigation.AppNavigator
import com.black.app.ui.navigation.AppRoute
import com.black.app.ui.theme.BlackTheme
import com.black.core.component.BaseFragment
import com.black.feature.pokerogue.R as PokeRogueR
import com.black.feature.pokerogue.ui.PokeRogueFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseFragment<FragmentMainBinding>() {

    // static list이고, 리소스 디펜던시가 있기 때문에 View에서 관리
    private val itemList by lazy {
        listOf(
            MainItem(
                getString(R.string.fragment_name_usage_timer),
                R.drawable.ic_timer,
            ) { navigateByActivity(AppRoute.UsageTimer) },
            MainItem(
                getString(R.string.fragment_name_text_editor),
                R.drawable.ic_editor,
            ) { navigateByActivity(AppRoute.TextEditor) },
            /** [PokeRogueFragment] */
            MainItem(
                getString(PokeRogueR.string.poke_rogue),
                PokeRogueR.drawable.ic_poke_rogue,
            ) { navigateByActivity(AppRoute.PokeRogue) },
            MainItem(
                getString(R.string.fragment_name_floating),
                R.drawable.ic_floating,
            ) { navigateByActivity(AppRoute.Floating) },
            MainItem(
                getString(R.string.fragment_name_black),
                R.drawable.ic_invisible,
            ) { navigateByActivity(AppRoute.Black) },
        )
    }

    @Inject
    lateinit var deeplinkManager: DeeplinkManager

    @Inject
    lateinit var appNavigator: AppNavigator

    override val layoutResId: Int = R.layout.fragment_main

    override fun onBindVariable(binding: FragmentMainBinding) {
        // ViewModel 생략
        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BlackTheme {
                    MainScreen(itemList)
                }
            }
        }
    }

    /** 루트 Compose NavController를 통한 화면 이동 */
    private fun navigateByActivity(route: AppRoute) {
        appNavigator.navigateSafely(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}
