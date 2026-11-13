package com.black.app.ui.main.keepscreenon

import android.content.ComponentName
import android.view.WindowManager
import com.black.app.testutil.BaseUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 빠른 설정 타일 클릭으로 이 앱의 화면 켜짐 유지 창이 붙고 떨어지는지 검증하는 계측 테스트
 *
 * cmd statusbar로 타일 추가·클릭, 판정은 dumpsys window의 이 앱 패키지 KeepScreenOn 창과 그 FLAG_KEEP_SCREEN_ON으로 한정
 * 화면이 실제로 꺼지지 않는지는 기기 전체 상태(실사용 앱·동영상 앱의 켜짐 유지 창, 화면 시간 제한)에 좌우되고
 * FLAG_KEEP_SCREEN_ON의 효과는 플랫폼이 보장하므로 판정에서 제외
 * 화면 꺼짐 시 자동 해제는 기기를 잠가 사용 중인 기기와 이후 테스트를 방해하므로 `KeepScreenOnGlobalTest`가 단위 테스트로 검증
 * 사용자가 테스트 전에 추가해 둔 타일과 이 앱의 화면 켜짐 유지 활성 여부는 테스트 후 그 상태로 되돌림
 *
 * 잠금 해제 상태에서 쓰는 기능이라 각 테스트가 화면을 켜고 잠금 해제를 시도한 뒤 그래도 잠겨 있으면 건너뜀
 * (잠금 중에는 시스템이 오버레이 창을 숨겨 FLAG_KEEP_SCREEN_ON 미적용, 보안 잠금은 테스트에서 해제 불가)
 *
 * 실제 시간 대기가 필요하므로 가상 시간으로 delay를 건너뛰는 runTest 대신 runBlocking 사용
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KeepScreenOnTileUiTest : BaseUiTest() {

    private val tileComponentName = ComponentName(context, KeepScreenOnTileService::class.java)
    private val tileComponent = tileComponentName.flattenToString()

    /** dumpsys window 창 정보에서 이 앱 패키지의 창을 고르는 패턴(실사용 앱 패키지는 이 앱 패키지의 접두어라 경계까지 일치) */
    private val ownPackagePattern = Regex("""package=${Regex.escape(context.packageName)}(\s|$)""")

    /**
     * 오버레이 표시 권한·타일·활성 여부를 테스트 후 복원하도록 BaseUiTest에 등록하며 변경
     *
     * BaseUiTest는 등록 역순으로 복원하므로 활성 여부 → 타일 → 오버레이 표시 권한 순으로 되돌림
     */
    override fun setup() {
        // 오버레이 표시 권한 부여(shell uid 권한으로 직접 허용, teardown 뒤 BaseUiTest가 테스트 전 모드로 복원)
        setAppOpMode("SYSTEM_ALERT_WINDOW", "allow")
        // 사용자가 추가해 둔 타일은 테스트 후 지우지 않도록 추가 여부 보관 후 빠른 설정창에 타일 추가
        preserveValue(
            read = { isTileAdded() },
            restore = { wasTileAdded ->
                // 테스트 전에 없던 타일만 제거
                if (!wasTileAdded) {
                    device.executeShellCommand("cmd statusbar remove-tile $tileComponent")
                }
            },
        )
        device.executeShellCommand("cmd statusbar add-tile $tileComponent")
        // 테스트 전 활성 여부 보관 후 같은 프로세스 싱글톤 비활성 상태로 시작
        // 활성 여부는 권한 등록 뒤에 등록해, 오버레이 창 추가에 쓰는 권한을 되돌리기 전에 복원
        preserveValue(
            read = { KeepScreenOnGlobal.isActive.value },
            restore = { wasActive ->
                instrumentation.runOnMainSync {
                    if (wasActive) {
                        KeepScreenOnGlobal.activate(context)
                    } else {
                        KeepScreenOnGlobal.deactivate(context)
                    }
                }
            },
        )
        instrumentation.runOnMainSync { KeepScreenOnGlobal.deactivate(context) }
    }

    /**
     * 빠른 설정창에 이 타일이 추가돼 있는지 여부
     *
     * 추가된 타일 목록 설정에서 TileService 타일은 `custom(컴포넌트)` 항목으로 저장
     */
    private fun isTileAdded(): Boolean {
        val tileSpecs = getSetting("secure", QS_TILES_SETTING)?.split(",").orEmpty().map { it.trim() }
        return tileSpecs.any {
            it == "custom(${tileComponentName.flattenToShortString()})" || it == "custom($tileComponent)"
        }
    }

    /**
     * 타일 클릭으로 이 앱의 화면 켜짐 유지 창이 FLAG_KEEP_SCREEN_ON을 가진 채 붙는지 검증
     *
     * Given: 잠금 해제 상태, 타일 추가, 비활성 상태, 이 앱의 KeepScreenOn 창 없음
     * When: 타일 클릭
     * Then: 활성 상태, dumpsys window에 이 앱 패키지의 KeepScreenOn 창이 FLAG_KEEP_SCREEN_ON을 가짐
     */
    @Test
    fun test_01_clickTileAddsKeepScreenOnWindow() = runBlocking {
        /** Given **/
        assumeUnlocked()
        assertNull("테스트 전부터 이 앱의 KeepScreenOn 창이 있습니다", ownKeepScreenOnWindow())

        /** When **/
        clickTile()

        /** Then **/
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil { KeepScreenOnGlobal.isActive.value })
        val isWindowAdded = waitUntil { ownKeepScreenOnWindow() != null }
        assertTrue("타일 클릭 후 이 앱의 KeepScreenOn 창이 없습니다", isWindowAdded)
        val window = ownKeepScreenOnWindow().orEmpty()
        assertTrue("KeepScreenOn 창에 FLAG_KEEP_SCREEN_ON이 없습니다: $window", hasKeepScreenOnFlag(window))
    }

    /**
     * 타일 재클릭으로 해제하면 이 앱의 화면 켜짐 유지 창이 떨어지는지 검증
     *
     * Given: 잠금 해제 상태, 타일 클릭으로 활성화, 이 앱의 KeepScreenOn 창 있음
     * When: 타일 재클릭
     * Then: 비활성 상태, dumpsys window에 이 앱 패키지의 KeepScreenOn 창 없음
     */
    @Test
    fun test_02_clickTileAgainRemovesKeepScreenOnWindow() = runBlocking {
        /** Given **/
        assumeUnlocked()
        clickTile()
        assertTrue("타일 클릭 후 활성화되지 않았습니다", waitUntil { KeepScreenOnGlobal.isActive.value })
        // 해제 판정이 의미 있도록 해제 전에는 이 앱의 창이 붙어 있는지 확인
        assertTrue(
            "타일 클릭 후 이 앱의 KeepScreenOn 창이 없습니다",
            waitUntil { ownKeepScreenOnWindow() != null },
        )

        /** When **/
        clickTile()

        /** Then **/
        assertTrue("타일 재클릭 후 해제되지 않았습니다", waitUntil { !KeepScreenOnGlobal.isActive.value })
        val isWindowRemoved = waitUntil { ownKeepScreenOnWindow() == null }
        assertTrue("해제 후에도 이 앱의 KeepScreenOn 창이 남아 있습니다: ${ownKeepScreenOnWindow()}", isWindowRemoved)
    }

    /**
     * 실제 사용 흐름(빠른 설정창 열기 → 타일 클릭 → 닫기)으로 타일 클릭
     *
     * 패널을 닫은 채 click-tile만 보내면 SystemUI 바인딩 요청 플래그가 남아 동결된 앱 프로세스로 클릭 미전달
     */
    private suspend fun clickTile() {
        device.executeShellCommand("cmd statusbar expand-settings")
        delay(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar click-tile $tileComponent")
        delay(PANEL_ANIMATION_DELAY)
        device.executeShellCommand("cmd statusbar collapse")
    }

    /**
     * dumpsys window의 창 정보 중 이 앱 패키지의 KeepScreenOn 창 정보 반환(없으면 null)
     *
     * 같은 기기의 실사용 앱도 같은 제목의 창을 만들므로 창 제목과 함께 `package=` 필드로 이 앱의 창만 선택
     */
    private fun ownKeepScreenOnWindow(): String? =
        device.executeShellCommand("dumpsys window windows")
            .split(WINDOW_ENTRY_REGEX)
            .firstOrNull { entry ->
                entry.lineSequence().first().contains(" ${KeepScreenOnGlobal.WINDOW_TITLE}}") &&
                    ownPackagePattern.containsMatchIn(entry)
            }

    /**
     * 창 정보의 flags(`fl=`)에 FLAG_KEEP_SCREEN_ON이 있는지 여부
     *
     * 기기마다 출력 형식이 달라 플래그 이름 목록(AOSP `fl=NOT_FOCUSABLE KEEP_SCREEN_ON …`)과
     * 16진수 값(삼성 One UI `fl=1000298`)을 모두 처리
     */
    private fun hasKeepScreenOnFlag(window: String): Boolean {
        val flags = FLAGS_LINE_REGEX.find(window)?.groupValues?.get(1)?.trim() ?: return false
        // 16진수 값이면 비트, 아니면 플래그 이름으로 판정(플래그 이름에는 밑줄 등 16진수가 아닌 문자 포함)
        flags.toLongOrNull(16)?.let { return (it and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.toLong()) != 0L }
        return flags.split(" ").contains(KEEP_SCREEN_ON_FLAG_NAME)
    }

    companion object {
        /** 빠른 설정창 펼침·접힘 및 타일 서비스 바인딩 대기 시간 */
        private const val PANEL_ANIMATION_DELAY = 1_500L
        /** 빠른 설정창에 추가된 타일 목록 설정 키(secure) */
        private const val QS_TILES_SETTING = "sysui_qs_tiles"
        /** 플래그 이름을 출력하는 기기에서 dumpsys window 창 속성 flags(`fl=`)에 표시되는 FLAG_KEEP_SCREEN_ON 이름 */
        private const val KEEP_SCREEN_ON_FLAG_NAME = "KEEP_SCREEN_ON"
        /** dumpsys window 창 정보에서 flags 값이 있는 줄(`      fl=…`, `pfl=` 줄 제외) */
        private val FLAGS_LINE_REGEX = Regex("""(?m)^\s*fl=(.*)$""")
        /** dumpsys window windows에서 창 하나의 정보가 시작되는 줄(`  Window #3 Window{… u0 제목}:`) */
        private val WINDOW_ENTRY_REGEX = Regex("""(?m)^\s*Window #\d+ """)
    }
}
