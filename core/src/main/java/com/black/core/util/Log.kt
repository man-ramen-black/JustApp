package com.black.core.util

import android.util.Log
import com.black.core.BuildConfig

object Log {
    private data class MethodInfo(val className: String, val packageName: String, val simpleName: String, val methodName: String, val lineNumber: Int)

    private const val LOG_SPLIT_LENGTH = 3000

    fun v(obj: Any? = "") {
        printLog("$obj", 'v')
    }

    fun v(tag: String, message: Any?) {
        printLog("$message", 'v', tag)
    }

    fun i(obj: Any? = "") {
        printLog("$obj", 'i')
    }

    fun i(tag: String, obj: Any?) {
        printLog("$obj", 'i', tag)
    }

    fun d(obj: Any? = "") {
        printLog("$obj", 'd')
    }

    fun d(tag: String, obj: Any?) {
        printLog("$obj", 'd', tag)
    }

    fun w(obj: Any? = "") {
        printLog("$obj", 'w')
    }

    fun w(tag: String, obj: Any?) {
        printLog("$obj", 'w', tag)
    }

    fun e(obj: Any? = "") {
        printLog("$obj", 'e')
    }

    fun e(tag: String, obj: Any?) {
        printLog("$obj", 'e', tag)
    }

    private fun printLog(message: String, level: Char, tag: String? = null) {
        val methodInfo = getMethodInfo()
        val logTag = tag ?: methodInfo.simpleName
        val prefix = methodInfo.methodName + ": ".takeIf { message.isNotBlank() }.orEmpty()
        val suffix = " [${methodInfo.simpleName}.${methodInfo.methodName}() : ${methodInfo.lineNumber}]"
        printLongLog(logTag, level, message, prefix, suffix)
    }

    /**
     * 로그캣 출력 제한(4068자)으로 로그가 잘리지 않도록 긴 로그 메시지를 분할해서 출력
     * 로그캣 출력 제한은 Terminal에서 "adb logcat -g" 입력 시 확인 가능
     */
    private fun printLongLog(tag: String, level: Char, longLogMessage: String, prefix: String, suffix: String) {
        // release 빌드에서 로그 미출력
        if (!BuildConfig.DEBUG) {
            return
        }

        val logList: List<String> = splitLog(longLogMessage)
        logList.forEachIndexed { index, log ->
            val logMessage = when (index) {
                0 -> if (logList.size == 1) {
                    prefix + log + suffix
                } else {
                    prefix + log
                }

                logList.lastIndex -> " \n$log$suffix"

                else -> " \n$log"
            }

            when (level) {
                'v' -> Log.v(tag, logMessage)
                'i' -> Log.i(tag, logMessage)
                'd' -> Log.d(tag, logMessage)
                'w' -> Log.w(tag, logMessage)
                'e' -> Log.e(tag, logMessage)
            }
        }
    }

    /**
     * 텍스트를 길이에 맞게 나눔
     * ex
     * split("12345", 2) => "12", "34", "5"
     */
    private fun splitLog(log: String): List<String> {
        if (log.isEmpty()) {
            return listOf("")
        }

        val list = ArrayList<String>()
        for (i in 0 .. log.length / LOG_SPLIT_LENGTH) {
            val start = i * LOG_SPLIT_LENGTH
            var end = (i + 1) * LOG_SPLIT_LENGTH
            end = end.coerceAtMost(log.length)
            list.add(log.substring(start, end))
        }
        return list
    }

    /**
     * Log를 호출한 클래스, 메소드 정보를 획득
     */
    private fun getMethodInfo() : MethodInfo {
        val stackTraceArr = Thread.currentThread().stackTrace

        // com.netmarble.nmapp.util.Log 다음에 오는 stackTrace를 검색 => Log를 호출한 StackTrace 검색
        var isStart = false
        var calledMethod: StackTraceElement? = null
        for (stackTrace in stackTraceArr) {
            if (stackTrace.className == this@Log::class.java.name && !isStart) {
                isStart = true
                continue
            }

            if (stackTrace.className != this@Log::class.java.name && isStart) {
                calledMethod = stackTrace
                break
            }
        }

        if (calledMethod == null) {
            return MethodInfo("Unknown", "Unknown", "Unknown", "Unknown", 0)
        }

        val classSplit = calledMethod.className.split("$").filter { it.length != 1 && it != "Companion" }

        // ex. com.netmarble.nmapp.util.Log
        val className = classSplit[0]

        // ex. NetworkHelper.callInternal or callInternal
        val methodName = classSplit.getOrNull(1)?.plus("$" + calledMethod.methodName)
            ?: calledMethod.methodName

        // ex.com.netmarble.nmapp
        val packageName = className.split(".")
            .takeIf { it.size >= 3 }
            ?.subList(0, 3)
            ?.joinToString(".")
            ?: "Unknown"

        // ex.Log
        val simpleName = className.substringAfterLast(".")

        return MethodInfo(className, packageName, simpleName, methodName, calledMethod.lineNumber)
    }
}