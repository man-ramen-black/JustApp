package com.black.study.util

import android.util.Log
import com.black.study.BuildConfig

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
        val className = methodInfo.first
        val logMessage = "$message ${methodInfo.second}"
        when (level) {
            'v' -> Log.v(className, logMessage)
            'i' -> Log.i(className, logMessage)
            'd' -> Log.d(className, logMessage)
            'w' -> Log.w(className, logMessage)
            'e' -> Log.e(className, logMessage)
        }
    }

    /**
     * @return "packageName" to "classSimpleName.methodName() : lineNumber"
     */
    private fun getMethodInfo() : Pair<String, String> {
        val stackTrace = Thread.currentThread().stackTrace.getOrNull(5)
            ?: return "Unknown" to "[Unknown]"
        val className = stackTrace.className
        val packageName = className.split(".").subList(0, 3).joinToString(".")
        val simpleName = className.substringAfterLast(".")
        return packageName to "[$simpleName.${stackTrace.methodName}() : ${stackTrace.lineNumber}]"
    }
}