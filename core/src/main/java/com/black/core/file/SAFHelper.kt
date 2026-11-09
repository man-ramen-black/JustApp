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
import com.black.core.util.Log
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
        /**
         * 문서를 고른 직후 재부팅 뒤에도 접근할 수 있도록 영구 권한 요청
         *
         * 선택 결과(Uri)만으로는 부여된 플래그를 알 수 없어 읽기·쓰기를 먼저 요청하고, 쓰기를 주지 않는 문서는 읽기만 요청.
         * 스트림을 열 때마다 읽기·쓰기를 요청하면 쓰기 권한이 없는 문서는 읽기도 실패하므로 선택 직후 한 번만 호출
         * @return 영구 권한을 받았으면 true
         */
        fun keepUriPermission(context: Context, uri: Uri): Boolean {
            val contentResolver = context.contentResolver
            return try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                true
            } catch (e: SecurityException) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                } catch (e: SecurityException) {
                    Log.w(e)
                    false
                }
            }
        }

        /**
         * 읽기 스트림 열기(파일이 없으면 null)
         *
         * 권한이 없는 uri는 SecurityException을 그대로 던짐. 호출부가 이 예외로 문서 재선택을 안내
         */
        @SuppressLint("Recycle")
        fun Uri.openInputStream(context: Context): InputStream? {
            return try {
                context.contentResolver.openInputStream(this)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 쓰기 스트림 열기(파일이 없으면 null)
         *
         * 권한이 없는 uri는 SecurityException을 그대로 던짐
         * @param mode "w" : 덮어쓰기, "a" : 내용 추가, "t" : 텍스트 모드, "b" : 바이너리 모드
         */
        @SuppressLint("Recycle")
        fun Uri.openOutputStream(context: Context, mode: String = "w"): OutputStream? {
            return try {
                context.contentResolver.openOutputStream(this, mode)
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

        /**
         * 표시용 파일 이름 조회
         *
         * 접근 권한이 없는 content uri는 null 반환. 재설치 등으로 영구 권한이 사라진 uri를 query하면
         * 문서 제공자가 SecurityException을 던짐
         */
        fun Uri.getFileName(context: Context): String? {
            return when (scheme) {
                "content" -> try {
                    context.contentResolver.query(this, null, null, null, null)
                        ?.use {
                            if (it.moveToFirst()) {
                                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                it.getString(nameIndex)
                            } else {
                                null
                            }
                        }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    null
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