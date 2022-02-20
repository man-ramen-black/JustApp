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
            'v' -> Log.v(methodInfo.className, logMessage)
            'i' -> Log.i(methodInfo.className, logMessage)
            'd' -> Log.d(methodInfo.className, logMessage)
            'w' -> Log.w(methodInfo.className, logMessage)
            'e' -> Log.e(methodInfo.className, logMessage)
        }
    }

    private data class MethodInfo(val className: String, val packageName: String, val simpleName: String, val methodName: String, val lineNumber: Int)

    private fun getMethodInfo() : MethodInfo {
        val stackTrace = Thread.currentThread().stackTrace.getOrNull(5)
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