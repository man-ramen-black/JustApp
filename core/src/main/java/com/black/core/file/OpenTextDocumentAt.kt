package com.black.core.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * text/plain 문서 열기 선택창을 입력 uri 문서가 있는 위치에서 여는 계약
 *
 * EXTRA_INITIAL_URI는 Android 8.0부터 지원하며, 이전 버전은 기본 위치에서 열림
 */
class OpenTextDocumentAt : ActivityResultContract<Uri, Uri?>() {
    private val openDocument = ActivityResultContracts.OpenDocument()

    override fun createIntent(context: Context, input: Uri): Intent {
        return openDocument.createIntent(context, arrayOf("text/plain")).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return openDocument.parseResult(resultCode, intent)
    }
}
