package com.black.app.model.preferences

import android.content.Context
import android.net.Uri
import com.black.core.model.BasePreferences

/**
 * Created by jinhyuk.lee on 2022/04/11
 **/
class TextEditorPreferences(context: Context) : com.black.core.model.BasePreferences(context, "TextEditor") {
    companion object {
        private const val KEY_LATEST_URI = "LatestUri"
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