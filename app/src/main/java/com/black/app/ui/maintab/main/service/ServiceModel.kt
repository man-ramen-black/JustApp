package com.black.app.ui.maintab.main.service

import android.content.Context
import com.black.app.service.ForegroundServiceController
import com.black.core.util.PermissionHelper
import kotlinx.coroutines.flow.first

/**
 * Created by jinhyuk.lee on 2022/05/07
 **/
class ServiceModel(private val context: Context) {
    private val controller = ForegroundServiceController(context)

    suspend fun startForegroundService() : Boolean {
        val result = controller.activate()
        // 컨트롤러는 화면 이동을 하지 않으므로 이 화면에서 배터리 최적화 설정으로 안내
        if (result == ForegroundServiceController.StartResult.BatteryOptimizationRequired) {
            PermissionHelper.showIgnoreBatteryOptimizationSettings(context, true)
        }
        return result == ForegroundServiceController.StartResult.Started
    }

    suspend fun stopForegroundService() {
        controller.deactivate()
    }

    suspend fun getForegroundServiceActivated() : Boolean {
        return controller.activated.first()
    }
}
