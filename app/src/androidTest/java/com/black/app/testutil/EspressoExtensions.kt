package com.black.app.testutil

import android.content.res.Resources
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry

/**
 * UI 테스트에서 반복되는 Espresso 검증·인터랙션을 함수화한다.
 *
 * 테스트 인스턴스 상태(scenario, rule 등)에 의존하지 않는 무상태 헬퍼이므로 최상위 함수로 두고,
 * 어느 테스트나 Robot 에서나 import 해서 사용한다.
 *
 * 검증·인터랙션이 실패하면 "어떤 뷰의 무엇이 실패했는지"를 메시지에 담아 [AssertionError]로 다시 던지므로,
 * 실패 로그만으로 원인을 바로 파악할 수 있다.
 */

/** [viewId] 뷰가 화면에 표시(isDisplayed)되는지 검증한다. */
fun assertDisplayed(@IdRes viewId: Int): ViewInteraction =
    checkView(viewId, "화면에 표시되지 않았습니다(isDisplayed)", matches(isDisplayed()))

/** [viewId] 뷰의 텍스트가 [text]와 일치하는지 검증한다. */
fun assertText(@IdRes viewId: Int, text: String): ViewInteraction =
    checkView(viewId, "텍스트가 \"$text\"와 일치하지 않습니다", matches(withText(text)))

/** [viewId] 뷰의 effectiveVisibility가 VISIBLE 인지 검증한다(스크롤 위치와 무관). */
fun assertEffectiveVisible(@IdRes viewId: Int): ViewInteraction =
    checkView(viewId, "effectiveVisibility가 VISIBLE이 아닙니다", matches(withEffectiveVisibility(Visibility.VISIBLE)))

/** [viewId] 뷰의 effectiveVisibility가 GONE 인지 검증한다. */
fun assertEffectiveGone(@IdRes viewId: Int): ViewInteraction =
    checkView(viewId, "effectiveVisibility가 GONE이 아닙니다", matches(withEffectiveVisibility(Visibility.GONE)))

/** [viewId] 뷰를 클릭한다. [scrollTo]가 true면 먼저 화면에 보이도록 스크롤한다. */
fun clickView(@IdRes viewId: Int, scrollTo: Boolean = false): ViewInteraction {
    val actions = if (scrollTo) {
        arrayOf(ViewActions.scrollTo(), ViewActions.click())
    } else {
        arrayOf(ViewActions.click())
    }
    return performOnView(viewId, "클릭에 실패했습니다", *actions)
}

/** [viewId] 뷰에 [text]를 입력하고 소프트 키보드를 닫는다. */
fun typeText(@IdRes viewId: Int, text: String): ViewInteraction =
    performOnView(viewId, "\"$text\" 입력에 실패했습니다", ViewActions.typeText(text), ViewActions.closeSoftKeyboard())

/** [viewId] 뷰에 [assertion]을 적용하고, 실패하면 뷰 이름과 [failureMessage]를 담아 다시 던진다. */
private fun checkView(@IdRes viewId: Int, failureMessage: String, assertion: ViewAssertion): ViewInteraction =
    try {
        onView(withId(viewId)).check(assertion)
    } catch (error: Throwable) {
        throw AssertionError("[${resourceName(viewId)}] $failureMessage", error)
    }

/** [viewId] 뷰에 [actions]를 수행하고, 실패하면 뷰 이름과 [failureMessage]를 담아 다시 던진다. */
private fun performOnView(@IdRes viewId: Int, failureMessage: String, vararg actions: ViewAction): ViewInteraction =
    try {
        onView(withId(viewId)).perform(*actions)
    } catch (error: Throwable) {
        throw AssertionError("[${resourceName(viewId)}] $failureMessage", error)
    }

/** [viewId]의 리소스 엔트리 이름(예: select_app_button)을 반환한다. 찾지 못하면 숫자 id를 반환한다. */
private fun resourceName(@IdRes viewId: Int): String =
    try {
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getResourceEntryName(viewId)
    } catch (notFound: Resources.NotFoundException) {
        viewId.toString()
    }
