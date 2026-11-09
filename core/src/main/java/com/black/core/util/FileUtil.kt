package com.black.core.util

import java.io.*
import kotlin.jvm.Throws

object FileUtil {
    /** UTF-8 텍스트 전체 읽기(열고 저장만 해도 파일이 바뀌지 않도록 줄바꿈 문자와 마지막 줄바꿈 보존) */
    @Throws(IOException::class)
    fun readText(inputStream: InputStream): String {
        return inputStream.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
    }

    @Throws(IOException::class)
    fun writeText(outputStream: OutputStream, onWrite: (writer: BufferedWriter) -> Unit) {
        outputStream.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.buffered()
                    .use(onWrite)
            }
        }
    }
}
