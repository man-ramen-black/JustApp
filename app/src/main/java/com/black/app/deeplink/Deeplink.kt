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
        const val TODO = "todo"
    }

    /** @param clearBackStack true면 Main까지 백스택 정리 후 이동 */
    data class Navigate(val route: AppRoute, val clearBackStack: Boolean = false) : Deeplink

    companion object {
        /** [PathNavigate]의 [path]로 이동하는 `black://navigate/<path>` Uri */
        fun navigateUri(path: String): Uri = Uri.Builder()
            .scheme(Scheme.APP)
            .authority(Host.NAVIGATE)
            .appendPath(path)
            .build()

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
                PathNavigate.TODO -> Navigate(AppRoute.AssistantTask, clearBackStack = true)
                else -> null
            }
        }
    }
}
