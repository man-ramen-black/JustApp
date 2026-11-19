package com.black.test

import android.util.Log
import io.mockk.every
import java.util.Collections

/**
 * [tag] 태그로 [Log.d]에 남은 메시지를 모으는 목록
 *
 * [BaseTest]가 [Log]를 정적 목업한 뒤(`@Before` 이후) 불러야 하며, 이후 그 태그의 [Log.d] 응답을 덮음.
 * 덮은 뒤에도 [BaseTest]처럼 표준 출력으로 남겨 실패를 진단할 때 로그가 보임.
 * OkHttp 스레드처럼 다른 스레드에서 남긴 로그도 받도록 동기화 목록에 모음.
 * 동기화 목록은 순회를 잠그지 않으므로 순회(`any` 등) 전에 `toList()`로 복사해 씀.
 */
fun captureDebugLogs(tag: String): List<String> {
    val logLines = Collections.synchronizedList(mutableListOf<String>())
    every { Log.d(tag, any()) } answers {
        val message: String = secondArg()
        println("$tag [D] $message")
        logLines.add(message)
        0
    }
    return logLines
}
