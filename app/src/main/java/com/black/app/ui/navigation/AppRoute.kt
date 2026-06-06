package com.black.app.ui.navigation

import kotlinx.serialization.Serializable

/** 루트 Compose Navigation 라우트 */
sealed interface AppRoute {
    @Serializable data object MainTab : AppRoute
    @Serializable data object Black : AppRoute
    @Serializable data object TextEditor : AppRoute
    @Serializable data object RecyclerView : AppRoute
    @Serializable data object Retrofit : AppRoute
    @Serializable data object Service : AppRoute
    @Serializable data object Alarm : AppRoute
    @Serializable data object Architecture : AppRoute
    @Serializable data object Launcher : AppRoute
    @Serializable data object StudyPopup : AppRoute
    @Serializable data object UsageTimer : AppRoute
    @Serializable data object Etc : AppRoute
    @Serializable data object Notification : AppRoute
    @Serializable data object PokeRogue : AppRoute
    @Serializable data object Floating : AppRoute
}
