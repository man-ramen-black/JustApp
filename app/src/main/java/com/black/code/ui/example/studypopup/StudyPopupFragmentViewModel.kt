package com.black.code.ui.example.studypopup

import android.net.Uri
import android.text.Html
import android.util.Xml
import androidx.lifecycle.MutableLiveData
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.model.preferences.StudyPopupPreferences
import com.black.code.ui.example.recyclerview.RecyclerViewData
import com.black.code.util.FileUtil
import com.black.code.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.*

/**
 * #MVVM
 * https://blog.gangnamunni.com/post/mvvm_anti_pattern/
 * [StudyPopupFragment]
 */
open class StudyPopupFragmentViewModel : EventViewModel() {
    companion object {
        const val EVENT_ATTACH_VIEW = "AttachView"
        const val EVENT_LOAD = "Load"
        const val EVENT_LOAD_LATEST = "LoadLatest"
        const val EVENT_SAVE_NEW_DOCUMENT = "SaveNewDocument"
        const val EVENT_SAVE_OVERWRITE = "SaveOverwrite"
        const val EVENT_TOAST = "Toast"
        const val EVENT_EDIT_CONTENTS = "EditContents"
        const val EVENT_UPDATE_RECYCLER_VIEW = "UpdateRecyclerView"
    }

    val text = MutableLiveData("")
    val path = MutableLiveData("")
    var openedFileUri : Uri? = null
    private val list = ArrayList<StudyPopupData>()
    private var preferences : StudyPopupPreferences? = null

    init {
        initList()
        updateRecyclerView()
    }

    fun setPreferences(preferences: StudyPopupPreferences) {
        this.preferences = preferences
    }

    private fun initList() {
        list.clear()
        list.add(StudyPopupData.FileManager())
    }

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

    fun onClickAttachView() {
        sendEvent(EVENT_ATTACH_VIEW)
    }

    fun onClickAdd() {
        list.add(StudyPopupData.Contents(""))
        updateRecyclerView()
        onItemClickContents(list.size - 1)
    }

    fun onClickLoad() {
        sendEvent(EVENT_LOAD)
    }

    fun onClickSave() {
        text.value = toXmlString(list)
        if (path.value.isNullOrEmpty()) {
            sendEvent(EVENT_SAVE_NEW_DOCUMENT)
        } else {
            sendEvent(EVENT_SAVE_OVERWRITE)
        }
    }

    private fun toXmlString(list: List<StudyPopupData>) : String {
        val writer = StringWriter()
        Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            for (element in list) {
                if (element !is StudyPopupData.Contents) {
                    continue
                }

                startTag("", "contents")
                text(element.text)
                endTag("", "contents")
            }
            endDocument()
        }
        return writer.toString()
    }

    fun loadFile(uri: Uri, path: String?, stream: InputStream?) {
        this.path.value = path ?: ""

        if (path == null || stream == null) {
            sendEvent(EVENT_TOAST, "onLoadedFile : path == $path, stream == $stream")
            return
        }

        initList()

        try {
            FileUtil.read(stream) {
                text.value = it
                Log.e("RawText : $it")

                val parser = Xml.newPullParser().apply {
                    setInput(StringReader(it))
                }

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.TEXT) {
                        list.add(StudyPopupData.Contents(parser.text))
                    }
                    eventType = parser.next()
                }
            }

            openedFileUri = uri
            preferences?.saveLatestUri(uri)
            sendEvent(EVENT_TOAST, "Loaded")
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        updateRecyclerView()
    }

    fun saveNewFile(uri: Uri, path: String?, stream: OutputStream?) {
        this.path.value = path ?: ""
        if (path == null) {
            sendEvent(EVENT_TOAST, "saveNewFile : path == null")
            return
        }
        saveOverwrite(uri, stream)
    }

    fun saveOverwrite(uri: Uri, stream: OutputStream?) {
        if (stream == null) {
            sendEvent(EVENT_TOAST, "save : stream == null")
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

    fun onItemClickContents(position: Int) {
        sendEvent(EVENT_EDIT_CONTENTS, ContentsItem(position, list[position] as StudyPopupData.Contents))
    }

    fun onItemClickDelete(position: Int) {
        list.removeAt(position)
        updateRecyclerView()
    }

    fun updateItem(position: Int, data: StudyPopupData) {
        list[position] = data
        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        sendEvent(EVENT_UPDATE_RECYCLER_VIEW, list)
    }

    data class ContentsItem(val position: Int, val data: StudyPopupData.Contents)
}