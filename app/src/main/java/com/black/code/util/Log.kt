package com.black.code.util

import android.util.Log
import com.black.code.BuildConfig

object Log {
    fun v(message: String = "") {
        printLog(message, 'v')
    }

    fun i(message: String = "") {
        printLog(message, 'i')
    }

    fun d(message: String = "") {
        printLog(message, 'd')
    }

    fun w(message: String = "") {
        printLog(message, 'w')
    }

    fun e(message: String = "") {
        printLog(message, 'e')
    }

    private fun printLog(message: String, level: Char) {
        // release 빌드 시에 로그 미출력 (그냥.. 참고용)
        if (!BuildConfig.DEBUG) {
            return
        }

        val methodInfo = getMethodInfo()
        val logMessage = "${methodInfo.methodName}: $message [${methodInfo.simpleName}.${methodInfo.methodName}() : ${methodInfo.lineNumber}]"
        when (level) {
            'v' -> Log.v(methodInfo.simpleName, logMessage)
            'i' -> Log.i(methodInfo.simpleName, logMessage)
            'd' -> Log.d(methodInfo.simpleName, logMessage)
            'w' -> Log.w(methodInfo.simpleName, logMessage)
            'e' -> Log.e(methodInfo.simpleName, logMessage)
        }
    }

    private data class MethodInfo(val className: String, val packageName: String, val simpleName: String, val methodName: String, val lineNumber: Int)

    private fun getMethodInfo() : MethodInfo {
        val stackTraceArr = Thread.currentThread().stackTrace
        val stackTraceIndex = 5
        val stackTrace = stackTraceArr.getOrNull(stackTraceIndex)
            ?.let {
                // 획득한 StackTrace 클래스명이 Log일 경우 다음 StackTrace 사용
                if (it.className == this@Log::class.java.name) {
                    stackTraceArr.getOrNull(stackTraceIndex + 1)
                } else {
                    it
                }
            }
            ?: return MethodInfo("Unknown", "Unknown", "Unknown", "Unknown", 0)


//        for (index in Thread.currentThread().stackTrace.indices) {
//            Log.d("getMethodInfo", "[$index] ${Thread.currentThread().stackTrace[index].className}")
//        }

        val className = stackTrace.className
        val packageName = className.split(".").subList(0, 3).joinToString(".")
        val simpleName = className.substringAfterLast(".")
        return MethodInfo(className, packageName, simpleName, stackTrace.methodName, stackTrace.lineNumber)
    }
}