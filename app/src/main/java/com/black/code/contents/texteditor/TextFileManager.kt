package com.black.code.contents.texteditor

import java.io.*

object TextFileManager {
    fun read(inputStream: InputStream, onRead: (text: String) -> Unit) {
        val builder = StringBuilder()
        inputStream.use { stream ->
            InputStreamReader(stream).use { reader ->
                reader.readLines()
                    .forEach() {
                        builder.append(it)
                    }
                onRead(builder.toString())
            }
        }
    }

    fun write(outputStream: OutputStream, onWrite: (writer: BufferedWriter) -> Unit) {
        outputStream.use { stream ->
            OutputStreamWriter(stream).use { writer ->
                writer.buffered()
                    .use(onWrite)
            }
        }
    }
}