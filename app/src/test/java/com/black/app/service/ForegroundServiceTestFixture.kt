package com.black.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.black.app.model.datastore.ForegroundServiceDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf

/** ForegroundService 실행 조건(배터리 최적화 예외, 실행 설정)을 Robolectric 섀도와 실제 DataStore에 구성하고 서비스 시작 요청을 기다리는 테스트 헬퍼 */
object ForegroundServiceTestFixture {

    /** 배터리 최적화 예외 여부 지정 */
    fun setIgnoringBatteryOptimizations(context: Context, ignored: Boolean) {
        shadowOf(context.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(context.packageName, ignored)
    }

    /**
     * 실행 설정 false 초기화
     *
     * 실제 DataStore가 프로세스 단일 인스턴스라 테스트 사이에 값이 남으므로 @Before·@After에서 호출
     */
    fun resetActivated(context: Context) {
        runTest { ForegroundServiceDataStore(context).updateActivated(false) }
    }

    /**
     * 다음 서비스 시작 인텐트가 기록될 때까지 실제 시간으로 최대 5초 대기
     *
     * 시작 요청이 테스트 스케줄러 밖(Dispatchers.Default)에서 실행 설정을 읽은 뒤 나가 가상 시간 진행으로는 기다릴 수 없음
     */
    suspend fun awaitNextStartedService(application: Application): Intent =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                var intent = shadowOf(application).peekNextStartedService()
                while (intent == null) {
                    delay(50)
                    intent = shadowOf(application).peekNextStartedService()
                }
                intent
            }
        }
}
