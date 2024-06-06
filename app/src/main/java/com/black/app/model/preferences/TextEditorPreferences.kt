package com.black.app.model.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.black.core.model.BasePreferences
import com.black.core.model.preferences

/**
 * Created by jinhyuk.lee on 2022/04/11
 **/
class TextEditorPreferences(context: Context) : BasePreferences(context) {

    companion object {
        private val Context.textEditorPreferences by preferences("TextEditor")

        private const val KEY_LATEST_URI = "LatestUri"
    }

    override fun getPreferences(context: Context): SharedPreferences {
        return context.textEditorPreferences
    }

    fun saveLatestUri(uri: Uri) {
        put(KEY_LATEST_URI, uri.toString())
    }

    fun loadLatestUri() : Uri? {
        val uriString = get(KEY_LATEST_URI, "")
        return if (uriString.isEmpty()) {
            null
        } else {
            Uri.parse(uriString)
        }
    }

    fun removeLatestUri() {
        remove(KEY_LATEST_URI)
    }
}