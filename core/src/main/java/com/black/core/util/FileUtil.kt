package com.black.core.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.*
import kotlin.jvm.Throws

object FileUtil {
    @Throws(IOException::class)
    fun readText(inputStream: InputStream, onRead: (text: String) -> Unit) {
        onRead(readText(inputStream))
    }

    @Throws(IOException::class)
    fun readText(inputStream: InputStream): String {
        return inputStream.use { stream ->
            InputStreamReader(stream).use { reader ->
                reader.readLines().joinToString("\n")
            }
        }
    }

    @Throws(IOException::class)
    fun writeText(outputStream: OutputStream, onWrite: (writer: BufferedWriter) -> Unit) {
        outputStream.use { stream ->
            OutputStreamWriter(stream).use { writer ->
                writer.buffered()
                    .use(onWrite)
            }
        }
    }

    /**
     * 권한 불필요
     * @return ex. /data/user/0/com.black.app/files
     */
    fun getPrivateFilesDir(context: Context): File {
        return context.filesDir
    }

    /**
     * 권한 불필요
     * @param type ex. [Environment.DIRECTORY_DOWNLOADS]
     * @return ex. /storage/emulated/0/Android/data/com.black.app/files
     */
    fun getExternalPrivateDir(context: Context, type: String? = null): File? {
        return context.getExternalFilesDir(type)
    }

    /**
     * READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE 권한 필요
     * Android 10(API 29) 이상에서는 EXTERNAL_STORAGE 권한이 동작하지 않아서 해당 코드 사용 불가능
     * Android 10(API 29) 이상에서 외부 스토리지 저장은 SAF를 통해서만 가능하고,
     * download 폴더 한정으로 ContentResolver를 통해 권한 없이 파일을 저장할 수 있음
     *
     * @param type ex. [Environment.DIRECTORY_DOWNLOADS]
     * @return ex. /storage/emulated/0/Download
     */
    fun getExternalPublicDir(type: String): File {
        return Environment.getExternalStoragePublicDirectory(type)
    }

    /**
     * (권장하지 않음) Uri to String
     * https://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
     */
    fun Uri.toFilePath(context: Context): String?
        = getPath(context, this)

    fun getPath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                val contentUri: Uri = when (type) {
                    "image" -> {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    else -> {
                        Log.e("Unknown content type")
                        return null
                    }
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }

        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)

        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs,null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}