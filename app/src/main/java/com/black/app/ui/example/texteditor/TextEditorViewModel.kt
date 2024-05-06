package com.black.app.ui.example.texteditor

import android.net.Uri
import android.view.View
import androidx.lifecycle.MutableLiveData
import com.black.app.base.viewmodel.EventViewModel
import com.black.app.model.preferences.TextEditorPreferences
import com.black.app.util.FileUtil
import com.black.app.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * MVVM
 * https://blog.gangnamunni.com/post/mvvm_anti_pattern/
 */
class TextEditorViewModel : EventViewModel() {
    companion object {
        const val EVENT_LOAD = "Load"
        const val EVENT_LOAD_LATEST = "LoadLatest"
        const val EVENT_SAVE_NEW_DOCUMENT = "SaveNewDocument"
        const val EVENT_SAVE_OVERWRITE = "SaveOverwrite"
        const val EVENT_CLEAR = "Clear"
        const val EVENT_TOAST = "Toast"
    }

    val text = MutableLiveData("")
    val path = MutableLiveData("")
    var openedFileUri : Uri? = null
    private var preferences : TextEditorPreferences? = null

    fun loadLatestFile() {
        if (!path.value.isNullOrEmpty()) {
            Log.i("File already loaded")
            return
        }

        val uri = preferences?.loadLatestUri() ?: run {
            Log.w("LatestFile is null")
            return
        }

        sendEvent(EVENT_LOAD_LATEST, uri)
    }

    fun setModel(preferences: TextEditorPreferences) {
        this.preferences = preferences
    }

    fun onClickNewFile() {
        if (path.value.isNullOrEmpty()) {
            return
        }
        event.send(EVENT_CLEAR)
    }

    fun onClickLoad(view: View?) {
        event.send(EVENT_LOAD)
    }

    fun onClickSave() {
        if (path.value.isNullOrEmpty()) {
            event.send(EVENT_SAVE_NEW_DOCUMENT)
        } else {
            event.send(EVENT_SAVE_OVERWRITE)
        }
    }

    fun loadFile(uri: Uri, path: String?, stream: InputStream?) {
        this.path.value = path ?: ""

        if (path == null || stream == null) {
            sendEvent(EVENT_TOAST, "onLoadedFile : path == $path, stream == $stream")
            return
        }

        try {
            FileUtil.read(stream) {
                text.value = it
            }
            openedFileUri = uri
            preferences?.saveLatestUri(uri)
            sendEvent(EVENT_TOAST, "Loaded")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun saveNewFile(uri: Uri, path: String?, stream: OutputStream?) {
        this.path.value = path ?: ""
        if (path == null) {
            event.send(EVENT_TOAST, "saveNewFile : path == null")
            return
        }
        saveOverwrite(uri, stream)
    }

    fun saveOverwrite(uri: Uri, stream: OutputStream?) {
        if (stream == null) {
            event.send(EVENT_TOAST, "save : stream == null")
            return
        }

        try {
            FileUtil.write(stream) {
                it.write(text.value)
            }
            openedFileUri = uri
            preferences?.saveLatestUri(uri)
            sendEvent(EVENT_TOAST, "Saved")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clear() {
        openedFileUri = null
        preferences?.removeLatestUri()
        path.value = ""
        text.value = ""
    }
}