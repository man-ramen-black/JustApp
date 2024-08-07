package com.black.core.file

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.black.core.util.Extensions.alsoIf
import com.black.core.util.Extensions.applyIf
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * Storage Acess Framework Helper(Android 4.4 (API 수준 19) 이상)
 * https://developer.android.com/guide/topics/providers/document-provider?hl=ko
 * Created by jinhyuk.lee on 2024/07/29
 **/
class SAFHelper(
    private val fragment: Fragment,
    private val createMimeType: String = "*/*",
) {
    companion object {
        /** 재부팅 시에도 해당 URI의 권한이 유지되도록 설정 */
        fun keepUriPermission(context: Context, uri: Uri) {
            context.contentResolver
                .takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
        }

        @SuppressLint("Recycle")
        fun Uri.openInputStream(context: Context, keepPermission: Boolean = true): InputStream? {
            return try {
                context.contentResolver
                    .applyIf({ keepPermission }) { keepUriPermission(context, this@openInputStream) }
                    .openInputStream(this)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        /**
         * @param mode "w" : 덮어쓰기, "a" : 내용 추가, "t" : 텍스트 모드, "b" : 바이너리 모드
         */
        @SuppressLint("Recycle")
        fun Uri.openOutputStream(context: Context, mode: String = "w", keepPermission: Boolean = true): OutputStream? {
            return try {
                context.contentResolver
                    .applyIf({ keepPermission }) { keepUriPermission(context, this@openOutputStream) }
                    .openOutputStream(this, mode)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        fun Uri.hasPermission(context: Context): Boolean {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val permission = context.checkUriPermission(this, android.os.Process.myPid(), android.os.Process.myUid(), flags)
            return permission == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun Uri.getFileName(context: Context): String? {
            return when (scheme) {
                "content" -> {
                    context.contentResolver.query(this, null, null, null, null)
                        ?.use {
                            if (it.moveToFirst()) {
                                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                it.getString(nameIndex)
                            } else {
                                null
                            }
                        }
                }

                "file" -> lastPathSegment

                else -> null
            }
        }
    }

    private var isInitialized = false
    private lateinit var openDocumentLauncher : ActivityResultLauncher<Array<String>>
    private lateinit var createDocumentLauncher : ActivityResultLauncher<String>

    private var continuation: CancellableContinuation<Uri?>? = null
    var latestLoadedUri: Uri? = null
        private set

    /** onAttach 또는 onCreate에서 호출 필요 */
    fun init() {
        isInitialized = true
        openDocumentLauncher = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument(), this::resumeUri)
        createDocumentLauncher = fragment.registerForActivityResult(ActivityResultContracts.CreateDocument(createMimeType), this::resumeUri)
    }

    suspend fun load(mimeTypes: Array<String> = arrayOf("*/*")): Uri? {
        if (!isInitialized) {
            throw IllegalStateException("Not initialized. Call init() first.")
        }
        openDocumentLauncher.launch(mimeTypes)
        return suspendCancellableCoroutine { continuation = it }
            .alsoIf({ it != null }) { latestLoadedUri = it }
    }

    suspend fun createFile(fileName: String = ""): Uri? {
        if (!isInitialized) {
            throw IllegalStateException("Not initialized. Call init() first.")
        }
        createDocumentLauncher.launch(fileName)
        return suspendCancellableCoroutine { continuation = it }
            .alsoIf({ it != null }) { latestLoadedUri = it }
    }

    private fun resumeUri(uri: Uri?) {
        continuation?.resume(uri)
        continuation = null
    }
}