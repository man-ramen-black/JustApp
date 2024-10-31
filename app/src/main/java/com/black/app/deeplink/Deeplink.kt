package com.black.app.deeplink

import android.net.Uri
import com.black.app.R
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
    
    data class NavigateSimple(val idRes: Int) : Deeplink

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
            return when (val page = uri.pathSegments.firstOrNull()) {
                PathNavigate.MEMO -> NavigateSimple(R.id.action_text_editor_with_clear)
                else -> null
            }
        }
    }
}