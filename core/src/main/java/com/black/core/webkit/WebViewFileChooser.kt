package com.black.core.webkit

import android.net.Uri
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.black.core.util.Log

/**
 * Created by jinhyuk.lee on 2022/05/20
 **/
class WebViewFileChooser(activityResultCaller: ActivityResultCaller) {
    private var openDocumentLauncher : ActivityResultLauncher<Array<String>>? = null
    private var openMultipleDocumentsLauncher : ActivityResultLauncher<Array<String>>? = null
    private var callback: ((uriList: List<Uri>) -> Unit)? = null

    init {
        registerOpenDocumentLauncher(activityResultCaller)
        registerOpenMultipleDocumentsLauncher(activityResultCaller)
    }

    fun showFileChooser(fileChooserParams: WebChromeClient.FileChooserParams?, callback: (uriList: List<Uri>) -> Unit) {
        this.callback = callback

        val acceptType = fileChooserParams?.acceptTypes
            ?.filter { !it.isNullOrEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.toTypedArray()
            ?: arrayOf("*/*")

        if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            openMultipleDocumentsLauncher?.launch(acceptType)
        } else {
            openDocumentLauncher?.launch(acceptType)
        }
    }

    private fun registerOpenDocumentLauncher(resultCaller: ActivityResultCaller) {
        openDocumentLauncher = registerForActivityResult(resultCaller, ActivityResultContracts.OpenDocument()) {
            callback?.invoke(it?.let { listOf(it) } ?: emptyList())
            callback = null
        }
    }

    private fun registerOpenMultipleDocumentsLauncher(resultCaller: ActivityResultCaller) {
        openMultipleDocumentsLauncher = registerForActivityResult(resultCaller, ActivityResultContracts.OpenMultipleDocuments()) {
            callback?.invoke(it)
            callback = null
        }
    }

    /**
     * ActivityResult 처리 등록
     */
    private fun <I, O> registerForActivityResult(resultCaller: ActivityResultCaller, contracts: ActivityResultContract<I, O>, callback: (O) -> Unit) : ActivityResultLauncher<I> {
        return try {
            resultCaller.registerForActivityResult(contracts, callback)
        } catch (e: IllegalStateException) {
            Log.e("WebViewFileChooser는 Activity.onCreate 또는 Fragment.onCreate에서 생성되어야 합니다.")
            throw IllegalStateException(e.message)
        }
    }
}