package com.black.app

import android.app.Application
import com.black.core.network.Network
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App: Application() {

    override fun onCreate() {
        super.onCreate()
        // 실사용 앱(stable)도 core의 debug 변형을 써서 core만으로는 빌드 타입을 가릴 수 없어 앱의 빌드 타입으로 결정
        Network.setLoggingEnabled(BuildConfig.BUILD_TYPE == "debug")
    }
}
