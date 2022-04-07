package com.black.code.contents.texteditor

import android.net.Uri
import android.view.View
import androidx.lifecycle.MutableLiveData
import com.black.code.base.viewmodel.EventViewModel
import java.io.InputStream
import java.io.OutputStream

/**
 * MVVM
 * https://blog.gangnamunni.com/post/mvvm_anti_pattern/
 */
class TextEditorViewModel : EventViewModel() {
    companion object {
        const val EVENT_LOAD = "Load"
        const val EVENT_SAVE_NEW_DOCUMENT = "SaveNewDocument"
        const val EVENT_SAVE_OVERWRITE = "SaveOverwrite"
        const val EVENT_CLEAR = "Clear"
        const val EVENT_TOAST = "Toast"
    }

    val text = MutableLiveData("")
    val path = MutableLiveData("")
    var openedFileUri : Uri? = null

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

    fun onLoadedFile(uri: Uri, path: String?, stream: InputStream?) {
        this.path.value = path ?: ""
        openedFileUri = uri

        if (path == null || stream == null) {
            event.send(EVENT_TOAST, "onLoadedFile : path == $path, stream == $stream")
            return
        }

        TextFileManager.read(stream) {
            text.value = it
        }
        event.send(EVENT_TOAST, "Loaded")
    }

    fun onCreatedFile(uri: Uri, path: String?, stream: OutputStream?) {
        this.path.value = path ?: ""
        if (path == null) {
            event.send(EVENT_TOAST, "onCreatedFile : path == null")
            return
        }
        save(uri, stream)
    }

    fun save(uri: Uri, stream: OutputStream?) {
        if (stream == null) {
            event.send(EVENT_TOAST, "save : stream == null")
            return
        }

        TextFileManager.write(stream) {
            it.write(text.value)
        }
        openedFileUri = uri
        event.send(EVENT_TOAST, "Saved")
    }

    fun clear() {
        openedFileUri = null
        path.value = ""
        text.value = ""
    }
}