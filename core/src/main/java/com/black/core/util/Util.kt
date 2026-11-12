package com.black.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

object Util {
    /** 딥링크 실행 */
    fun startDeepLink(context: Context?, url: String?)
            = startDeepLink(context, Uri.parse(url ?: ""))

    fun startDeepLink(context: Context?, uri: Uri) {
        if(context == null){
            Log.w("context is null")
            return
        }

        val scheme = uri.scheme
        if (scheme == null) {
            Log.e("invalid uri : $uri")
            return
        }

        Log.v("deeplink : $uri")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if(context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}