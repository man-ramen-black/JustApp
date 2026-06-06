package com.black.app.deeplink

import android.net.Uri
import com.black.app.ui.navigation.AppRoute
import com.black.core.util.Log

/**
 * Uri -> Deeplink 파싱
 **/
sealed interface Deeplink {
    object Scheme {
        const val APP = "black"
    }

    object Host {
        const val NAVIGATE = "navigate"
    }

    object PathNavigate {
        const val MEMO = "memo"
    }

    /**
     * @param clearBackStack true면 MainTab까지 백스택 정리 후 이동
     */
    data class Navigate(val route: AppRoute, val clearBackStack: Boolean = false) : Deeplink

    companion object {
        fun parse(uri: Uri?): Deeplink? {
            Log.d(uri)
            uri ?: return null

            return when (uri.scheme) {
                Scheme.APP -> {
                    when (uri.host) {
                        Host.NAVIGATE -> {
                            parseNavigateDeeplink(uri)
                        }
                        else -> null
                    }
                }
                else -> null
            }
        }

        private fun parseNavigateDeeplink(uri: Uri): Deeplink? {
            return when (uri.pathSegments.firstOrNull()) {
                PathNavigate.MEMO -> Navigate(AppRoute.TextEditor, clearBackStack = true)
                else -> null
            }
        }
    }
}
