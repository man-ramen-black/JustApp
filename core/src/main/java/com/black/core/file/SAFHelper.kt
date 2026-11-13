package com.black.core.file

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.black.core.util.FileUtil
import com.black.core.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Acess Framework Helper(Android 4.4 (API 수준 19) 이상)
 * https://developer.android.com/guide/topics/providers/document-provider?hl=ko
 * Created by jinhyuk.lee on 2024/07/29
 **/
object SAFHelper {
    /**
     * 문서를 고른 직후 재부팅 뒤에도 접근할 수 있도록 영구 권한 요청(시스템 호출이라 IO 스레드에서 실행)
     *
     * 선택 결과(Uri)만으로는 부여된 플래그를 알 수 없어 읽기·쓰기를 먼저 요청하고, 쓰기를 주지 않는 문서는 읽기만 요청.
     * 스트림을 열 때마다 읽기·쓰기를 요청하면 쓰기 권한이 없는 문서는 읽기도 실패하므로 선택 직후 한 번만 호출
     * @return 영구 권한을 받았으면 true
     */
    suspend fun keepUriPermission(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        try {
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
     * 문서의 UTF-8 텍스트를 읽고 [onRead] 결과 반환, [onRead]까지 IO 스레드에서 실행
     *
     * 스트림을 열 수 없으면 [FileNotFoundException], 권한이 없으면 SecurityException 실패 결과.
     * [onRead](파싱, 마지막 문서 기록 등)의 예외도 같은 실패 결과로 묶고, 코루틴 취소는 결과로 바꾸지 않고 다시 던짐
     */
    suspend fun <T> Uri.readDocumentText(context: Context, onRead: suspend (text: String) -> T): Result<T> {
        val uri = this
        return resultOnIo {
            val stream = uri.openInputStream(context)
                ?: throw FileNotFoundException("openInputStream failed, uri : $uri")
            onRead(FileUtil.readText(stream))
        }
    }

    /**
     * [createText] 결과로 문서 내용을 바꾸고 [onWritten] 실행, 모두 IO 스레드에서 실행
     *
     * 기존 내용을 지우며 여는 "wt" 모드라 [createText]를 스트림을 열기 전에 실행해, 텍스트를 만들다 실패하면 문서를 건드리지 않음.
     * 스트림을 열 수 없으면 [FileNotFoundException], 권한이 없으면 SecurityException 실패 결과.
     * [onWritten](마지막 문서 기록 등)의 예외도 같은 실패 결과로 묶고, 코루틴 취소는 결과로 바꾸지 않고 다시 던짐
     */
    suspend fun Uri.writeDocumentText(
        context: Context,
        createText: () -> String,
        onWritten: suspend () -> Unit = {},
    ): Result<Unit> {
        val uri = this
        return resultOnIo {
            val text = createText()
            // wt : 스트림을 열면서 기존 파일 내용을 지움
            val stream = uri.openOutputStream(context, "wt")
                ?: throw FileNotFoundException("openOutputStream failed, uri : $uri")
            FileUtil.writeText(stream) { it.write(text) }
            onWritten()
        }
    }

    /** IO 스레드에서 [block]을 실행해 예외를 실패 결과로 반환, 코루틴 취소는 BaseDataStore 저장과 같이 다시 던짐 */
    private suspend fun <T> resultOnIo(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
            Log.w(e)
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
            Log.w(e)
            null
        }
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
                Log.w(e)
                null
            }

            "file" -> lastPathSegment

            else -> null
        }
    }
}
