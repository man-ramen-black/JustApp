package com.black.app.di

import com.black.app.service.ForegroundServiceController
import com.black.core.service.ForegroundServiceControl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 기능 모듈이 주입받는 core [ForegroundServiceControl]에 앱 [ForegroundServiceController] 연결 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ForegroundServiceModule {
    @Binds
    abstract fun bindForegroundServiceControl(controller: ForegroundServiceController): ForegroundServiceControl
}
