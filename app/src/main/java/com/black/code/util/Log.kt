package com.black.code.util

import android.util.Log
import com.black.code.BuildConfig

object Log {

    private const val LOG_SPLIT_LENGTH = 3000

    fun v(message: String = "") {
        printLog(message, 'v')
    }

    fun v(tag: String, message: String = "") {
        printLog(message, 'v', tag)
    }

    fun i(message: String = "") {
        printLog(message, 'i')
    }

    fun i(tag: String, message: String = "") {
        printLog(message, 'w', tag)
    }

    fun d(message: String = "") {
        printLog(message, 'd')
    }

    fun d(tag: String, message: String = "") {
        printLog(message, 'w', tag)
    }

    fun w(message: String = "") {
        printLog(message, 'w')
    }

    fun w(tag: String, message: String = "") {
        printLog(message, 'w', tag)
    }

    fun e(message: String = "") {
        printLog(message, 'e')
    }

    fun e(tag: String, message: String = "") {
        printLog(message, 'e', tag)
    }

    private fun printLog(message: String, level: Char, tag: String? = null) {
        // release 빌드 시에 로그 미출력
        if (!BuildConfig.DEBUG) {
            return
        }

        val methodInfo = getMethodInfo()
        val logTag = tag ?: methodInfo.simpleName
        val prefix = "${methodInfo.methodName}: "
        val suffix = " [${methodInfo.simpleName}.${methodInfo.methodName}() : ${methodInfo.lineNumber}]"
        printLongLog(logTag, level, message, prefix, suffix)
    }

    private fun getMethodInfo() : MethodInfo {
        val stackTraceArr = Thread.currentThread().stackTrace

        // com.netmarble.proto.util.Log 다음에 오는 stackTrace를 검색 => Log를 호출한 StackTrace 검색
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

        val classSplit = calledMethod.className.split("$")

        // ex. com.netmarble.proto.util.Log
        val className = classSplit[0]

        // ex. NetworkHelper.callInternal or callInternal
        val methodName = classSplit.getOrNull(1)?.plus("$" + calledMethod.methodName)
            ?: calledMethod.methodName

        // ex.com.netmarble.proto
        val packageName = className.split(".").subList(0, 3).joinToString(".")

        // ex.Log
        val simpleName = className.substringAfterLast(".")

        return MethodInfo(className, packageName, simpleName, methodName, calledMethod.lineNumber)
    }

    /**
     * 로그캣 출력 제한(4068자)으로 로그가 잘리지 않도록 긴 로그 메시지를 분할해서 출력
     * 로그캣 출력 제한은 Terminal에서 "adb logcat -g" 입력 시 확인 가능
     */
    private fun printLongLog(tag: String, level: Char, longLogMessage: String, prefix: String, suffix: String) {
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
        val list = ArrayList<String>()
        if (log.isEmpty()) {
            return listOf("")
        }

        for (i in 0 .. log.length / LOG_SPLIT_LENGTH) {
            val start = i * LOG_SPLIT_LENGTH
            var end = (i + 1) * LOG_SPLIT_LENGTH
            end = end.coerceAtMost(log.length)
            list.add(log.substring(start, end))
        }
        return list
    }

    private data class MethodInfo(val className: String,
                                  val packageName: String,
                                  val simpleName: String,
                                  val methodName: String,
                                  val lineNumber: Int)
}