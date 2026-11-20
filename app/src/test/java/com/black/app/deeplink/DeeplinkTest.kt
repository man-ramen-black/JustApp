package com.black.app.deeplink

import android.net.Uri
import com.black.app.ui.navigation.AppRoute
import com.black.feature.dday.widget.DDAY_SETTINGS_DEEPLINK_URI
import com.black.feature.todo.notification.TodoNotifier
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [Deeplink.parse]의 navigate 경로 해석과 [Deeplink.navigateUri]의 이동 Uri 생성, Todo 알림·D-Day 위젯 딥링크 해석 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DeeplinkTest : BaseTest() {

    /**
     * todo 경로가 Todo 화면 이동으로 해석되는지 검증
     *
     * Given: black://navigate/todo
     * When: parse
     * Then: Navigate(AppRoute.Todo, clearBackStack = true)
     */
    @Test
    fun test_01_todoPathNavigatesToTodo() = runTest {
        /** Given / When **/
        val deeplink = Deeplink.parse(Uri.parse("black://navigate/todo"))

        /** Then **/
        assertEquals(Deeplink.Navigate(AppRoute.Todo, clearBackStack = true), deeplink)
    }

    /**
     * memo 경로는 그대로 텍스트 편집 이동이고 모르는 경로는 null인지 검증
     *
     * Given: black://navigate/memo, black://navigate/unknown
     * When: parse
     * Then: TextEditor 이동, null
     */
    @Test
    fun test_02_memoStillWorksAndUnknownIsNull() = runTest {
        /** Given / When / Then **/
        assertEquals(
            Deeplink.Navigate(AppRoute.TextEditor, clearBackStack = true),
            Deeplink.parse(Uri.parse("black://navigate/memo")),
        )
        assertNull(Deeplink.parse(Uri.parse("black://navigate/unknown")))
    }

    /**
     * navigate 경로 상수로 만든 Uri가 딥링크 주소와 같고 다시 해석하면 그 화면 이동인지 검증
     *
     * Given: PathNavigate.TODO
     * When: navigateUri로 Uri 생성 후 parse
     * Then: black://navigate/todo, Navigate(AppRoute.Todo, clearBackStack = true)
     */
    @Test
    fun test_03_navigateUriBuildsParsableDeeplink() = runTest {
        /** Given / When **/
        val uri = Deeplink.navigateUri(Deeplink.PathNavigate.TODO)

        /** Then **/
        assertEquals("black://navigate/todo", uri.toString())
        assertEquals(Deeplink.Navigate(AppRoute.Todo, clearBackStack = true), Deeplink.parse(uri))
    }

    /**
     * Todo 알림이 여는 딥링크가 앱 딥링크 해석에서 Todo 화면 이동인지 검증
     *
     * Todo 모듈은 앱 모듈을 참조할 수 없어 딥링크 주소를 문자열로 들고 있으므로, 앱의 경로 상수와 어긋나지 않는지 확인함.
     *
     * Given: TodoNotifier.TODO_DEEPLINK_URI
     * When: parse
     * Then: Navigate(AppRoute.Todo, clearBackStack = true), navigateUri(PathNavigate.TODO)와 같은 주소
     */
    @Test
    fun test_04_todoNotifierDeeplinkNavigatesToTodo() = runTest {
        /** Given **/
        val uri = TodoNotifier.TODO_DEEPLINK_URI

        /** When **/
        val deeplink = Deeplink.parse(uri)

        /** Then **/
        assertEquals(Deeplink.Navigate(AppRoute.Todo, clearBackStack = true), deeplink)
        assertEquals(Deeplink.navigateUri(Deeplink.PathNavigate.TODO), uri)
    }

    /**
     * dday 경로가 D-Day 설정 화면 이동으로 해석되고 위젯의 딥링크 주소와 같은지 검증
     *
     * 디데이 모듈은 앱 모듈을 참조할 수 없어 딥링크 주소를 문자열로 들고 있으므로, 앱의 경로 상수와 어긋나지 않는지 확인함.
     *
     * Given: black://navigate/dday, DDAY_SETTINGS_DEEPLINK_URI
     * When: parse
     * Then: Navigate(AppRoute.Dday, clearBackStack = true), navigateUri(PathNavigate.DDAY)와 같은 주소
     */
    @Test
    fun test_05_ddayPathNavigatesToDday() = runTest {
        /** Given / When **/
        val deeplink = Deeplink.parse(Uri.parse("black://navigate/dday"))
        val widgetDeeplink = Deeplink.parse(DDAY_SETTINGS_DEEPLINK_URI)

        /** Then **/
        assertEquals(Deeplink.Navigate(AppRoute.Dday, clearBackStack = true), deeplink)
        assertEquals(deeplink, widgetDeeplink)
        assertEquals(Deeplink.navigateUri(Deeplink.PathNavigate.DDAY), DDAY_SETTINGS_DEEPLINK_URI)
    }
}
