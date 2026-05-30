package com.black.app.ui.maintab.main.usagetimer

import com.black.app.ui.maintab.main.usagetimer.UsageTimerGlobal.ForegroundAction
import com.black.test.BaseTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [UsageTimerGlobal.resolveForegroundAction] 포그라운드 창 동작 판정 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageTimerGlobalTest : BaseTest() {

    /**
     * 선택 앱 Activity가 포그라운드이고 일시정지가 아니면 표시
     *
     * Given: 선택 앱 Activity 창, 일시정지 아님
     * When: resolveForegroundAction 호출
     * Then: SHOW
     */
    @Test
    fun test_01_showWhenSelectedActivityAndNotPaused() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.SHOW,
            UsageTimerGlobal.resolveForegroundAction(true, "com.a", listOf("com.a", "com.b"), false),
        )
    }

    /**
     * 선택 앱의 비-Activity 창(다이얼로그 등)이어도 같은 앱이면 표시
     *
     * Given: 선택 앱이지만 비-Activity 창, 일시정지 아님
     * When: resolveForegroundAction 호출
     * Then: SHOW
     */
    @Test
    fun test_02_showWhenSelectedNonActivityWindow() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.SHOW,
            UsageTimerGlobal.resolveForegroundAction(false, "com.a", listOf("com.a"), false),
        )
    }

    /**
     * 일시정지 상태면 선택 앱이라도 숨김
     *
     * Given: 선택 앱 Activity 창, 일시정지 상태
     * When: resolveForegroundAction 호출
     * Then: HIDE
     */
    @Test
    fun test_03_hideWhenSelectedButPaused() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.HIDE,
            UsageTimerGlobal.resolveForegroundAction(true, "com.a", listOf("com.a"), true),
        )
    }

    /**
     * 선택 목록에 없는 다른 앱 Activity로 전환되면 숨김
     *
     * Given: 비선택 앱 Activity 창
     * When: resolveForegroundAction 호출
     * Then: HIDE
     */
    @Test
    fun test_04_hideWhenOtherActivity() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.HIDE,
            UsageTimerGlobal.resolveForegroundAction(true, "com.c", listOf("com.a", "com.b"), false),
        )
    }

    /**
     * 선택 목록에 없는 비-Activity 창(IME·오버레이·시스템 UI)은 현재 상태 유지
     *
     * Given: 비선택 패키지의 비-Activity 창
     * When: resolveForegroundAction 호출
     * Then: IGNORE (타이머가 즉시 사라지지 않음)
     */
    @Test
    fun test_05_ignoreWhenNonSelectedNonActivityWindow() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.IGNORE,
            UsageTimerGlobal.resolveForegroundAction(false, "com.android.inputmethod", listOf("com.a"), false),
        )
    }

    /**
     * packageName이 null이면 현재 상태 유지
     *
     * Given: packageName null
     * When: resolveForegroundAction 호출
     * Then: IGNORE
     */
    @Test
    fun test_06_ignoreWhenNullPackage() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.IGNORE,
            UsageTimerGlobal.resolveForegroundAction(false, null, listOf("com.a"), false),
        )
    }

    /**
     * 선택 목록이 비어있고 다른 앱 Activity면 숨김
     *
     * Given: 선택 목록 비어있음, Activity 창
     * When: resolveForegroundAction 호출
     * Then: HIDE
     */
    @Test
    fun test_07_hideWhenSelectedAppsEmptyAndActivity() {
        /** Given / When / Then **/
        assertEquals(
            ForegroundAction.HIDE,
            UsageTimerGlobal.resolveForegroundAction(true, "com.a", emptyList(), false),
        )
    }
}
