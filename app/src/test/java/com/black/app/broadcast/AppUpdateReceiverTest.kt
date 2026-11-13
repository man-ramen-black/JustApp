package com.black.app.broadcast

import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import com.black.app.model.datastore.ForegroundServiceDataStore
import com.black.app.service.ForegroundService
import com.black.test.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [AppUpdateReceiver]의 앱 업데이트 수신 후 ForegroundService 시작 요청 검증 */
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppUpdateReceiverTest : BaseTest() {

    private val application = RuntimeEnvironment.getApplication()
    private val dataStore = ForegroundServiceDataStore(application)

    /** 프로세스 단일 DataStore의 실행 설정 초기화 */
    @After
    fun clearActivated() {
        runTest { dataStore.updateActivated(false) }
    }

    /**
     * 앱 업데이트 action을 받으면 실행 설정이 켜진 ForegroundService 시작을 요청하는지 검증
     *
     * Given: 실행 설정 true, 배터리 최적화 예외
     * When: MY_PACKAGE_REPLACED action으로 AppUpdateReceiver를 지정한 브로드캐스트 전송
     * Then: ForegroundService 시작 인텐트 기록
     */
    @Test
    fun test_01_packageReplacedStartsActivatedService() = runTest {
        /** Given **/
        dataStore.updateActivated(true)
        shadowOf(application.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(application.packageName, true)

        /** When **/
        application.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(application, AppUpdateReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        /** Then **/
        // 시작 확인은 goAsync로 이어 가는 Dispatchers.Default 범위에서 DataStore를 읽으므로 실제 시간으로 대기
        val startedService = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                var intent = shadowOf(application).peekNextStartedService()
                while (intent == null) {
                    delay(50)
                    intent = shadowOf(application).peekNextStartedService()
                }
                intent
            }
        }
        assertEquals(ForegroundService::class.java.name, startedService.component?.className)
    }
}
