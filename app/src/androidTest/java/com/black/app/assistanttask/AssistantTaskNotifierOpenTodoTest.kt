package com.black.app.assistanttask

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import com.black.app.R
import com.black.app.model.AssistantTaskConnection
import com.black.app.model.AssistantTaskSettingsRepository
import com.black.app.testutil.BaseUiTest
import com.black.app.ui.MainActivity
import com.black.app.ui.main.assistanttask.AssistantTaskTestTags
import com.black.core.util.PermissionHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [AssistantTaskNotifier] 알림을 누르면 여는 인텐트가 실제로 Todo 화면을 여는지 검증하는 계측 테스트
 *
 * 알림 PendingIntent와 같은 [AssistantTaskNotifier.openTodoActivityIntent]로 [MainActivity]를 띄워 딥링크 이동까지 실제 경로로 확인.
 * 개발 앱에서 돌며, 노션 연결 설정을 비워 화면 재개 때의 동기화가 노션을 부르지 않게 함
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssistantTaskNotifierOpenTodoTest : BaseUiTest() {

    /** 테스트가 띄운 화면, [teardown]에서 노션 연결 복원 전에 닫음 */
    private var launchedScenario: ActivityScenario<MainActivity>? = null

    override fun setup() {
        clearNotionConnection()
        grantNotificationPermission()
    }

    /**
     * 노션 연결을 복원하기 전에 화면을 닫음
     *
     * [BaseUiTest]는 저장값을 복원한 뒤 scenario를 닫아, 화면이 살아 있는 동안 연결이 돌아오면 늦게 온 `ON_RESUME`의 동기화가 실제 노션을 부를 수 있음.
     * [BaseUiTest]가 뒤에서 같은 scenario를 다시 닫아도 이미 끝난 화면이라 아무 일도 없음.
     */
    override fun teardown() {
        launchedScenario?.close()
        launchedScenario = null
    }

    /**
     * 노션 연결 설정을 테스트 전 값으로 보관한 뒤 비움
     *
     * 연결이 있으면 Todo 화면이 재개될 때 동기화가 실제 노션을 부름.
     * `AssistantTaskSynchronizer.replaceConnection`은 데이터베이스가 바뀌면 저장본을 비우므로 설정 저장소를 바로 써서 저장본은 그대로 둠.
     */
    private fun clearNotionConnection() {
        val settings = AssistantTaskSettingsRepository(context)
        preserveValue(
            read = { settings.getConnection() },
            restore = { connection -> settings.saveConnection(connection).getOrThrow() },
        )
        runBlocking { settings.saveConnection(AssistantTaskConnection("", "")).getOrThrow() }
    }

    /**
     * API 33 이상에서 알림 권한 허용
     *
     * [MainActivity]가 첫 생성 때 알림 권한을 요청해 시스템 창이 화면을 덮지 않도록 미리 허용하고, 테스트 전에 거부였으면 프로세스가 끝날 때 되돌림.
     */
    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        preserveValue(
            read = { PermissionHelper.isGranted(context, permission) },
            restore = { wasGranted ->
                // 테스트 전에 허용이었으면 그대로 둠
                if (!wasGranted) context.revokeSelfPermissionsOnKill(listOf(permission))
            },
        )
        // 이미 허용된 권한이면 상태가 바뀌지 않음
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }

    /**
     * 알림 인텐트로 MainActivity를 띄우면 Todo 화면이 열리는지 검증
     *
     * Given:
     * - 알림이 쓰는 Todo 열기 인텐트(명시적 MainActivity, black://navigate/todo)
     * When:
     * - 그 인텐트로 MainActivity 실행
     * Then:
     * - Todo 화면의 설정 버튼이 생기고 상단 바 제목 Todo 표시
     */
    @Test
    fun test_01_openTodoIntentShowsTodoScreen() {
        /** Given **/
        val intent = AssistantTaskNotifier.openTodoActivityIntent(context)

        /** When **/
        launchedScenario = launchActivity<MainActivity>(intent)

        /** Then **/
        // 메인 화면 목록에도 Todo 항목이 있어 Todo 화면에만 있는 버튼으로 이동 완료를 먼저 확인
        waitUntilTagExists(AssistantTaskTestTags.SETTINGS_BUTTON)
        // 이름이 같은 저장본 Todo나 전환 중 남은 메인 목록 항목과 구분하도록 설정 버튼과 나란한 상단 바 제목만 찾음
        val title = context.getString(R.string.fragment_name_assistant_task)
        composeRule.onNode(hasText(title) and hasAnySibling(hasTestTag(AssistantTaskTestTags.SETTINGS_BUTTON)))
            .assertIsDisplayed()
    }
}
