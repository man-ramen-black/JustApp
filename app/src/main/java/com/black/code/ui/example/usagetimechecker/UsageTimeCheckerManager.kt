package com.black.code.ui.example.usagetimechecker

import android.content.Context
import android.content.Intent
import com.black.code.broadcast.ScreenReceiver
import com.black.code.service.ForegroundService
import com.black.code.util.Log

/**
 * ForegroundService, ScreenReceiver에서 UsageTimeChecker 동작 구현
 * Created by jinhyuk.lee on 2022/04/29
 **/
object UsageTimeCheckerManager : ForegroundService.Interface, ScreenReceiver.Interface {
    private const val ACTION_ATTACH = "UsageTimeChecker.ATTACH"
    private const val ACTION_DETACH = "UsageTimeChecker.DETACH"

    private var usageTimeCheckerView : UsageTimeCheckerView? = null

    override fun onStartCommand(context: Context, intent: Intent, flags: Int, startId: Int) {
        when(intent.action) {
            ACTION_ATTACH -> {
                usageTimeCheckerView = UsageTimeCheckerView(context).also {
                    it.attachView()
                }
            }

            ACTION_DETACH -> {
                usageTimeCheckerView ?: run {
                    Log.w("usageTimeCheckerView not attached")
                }
                usageTimeCheckerView?.detachView()
            }
        }
    }

    override fun onCreate(context: Context) {

    }

    override fun onDestroy(context: Context) {

    }

    override fun onScreenOn(context: Context, intent: Intent) {
        ForegroundService.start(context) {
            it.action = ACTION_ATTACH
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
        ForegroundService.start(context) {
            it.action = ACTION_DETACH
        }
    }
}