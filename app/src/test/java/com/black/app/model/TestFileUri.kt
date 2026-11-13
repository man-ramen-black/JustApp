package com.black.app.model

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * filesDir 아래 [name] 파일의 file Uri
 *
 * [content]가 있으면 그 내용으로 파일을 쓰고, null이면 파일을 만들지 않음(없는 파일 읽기 검증용).
 * 저장 결과 확인은 검증 대상과 같은 읽기 경로로 결함이 가려지지 않도록 각 테스트가 File로 직접 읽음
 */
fun Context.testFileUri(name: String, content: String? = null): Uri {
    val file = File(filesDir, name)
    content?.let { file.writeText(it) }
    return Uri.fromFile(file)
}
