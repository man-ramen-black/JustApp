package com.black.app.deeplink

import android.net.Uri
import com.black.app.ui.navigation.AppRoute
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [Deeplink.parse]의 navigate 경로 해석과 [Deeplink.navigateUri]의 이동 Uri 생성 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DeeplinkTest : BaseTest() {

    /**
     * todo 경로가 Todo 화면 이동으로 해석되는지 검증
     *
     * Given: black://navigate/todo
     * When: parse
     * Then: Navigate(AppRoute.AssistantTask, clearBackStack = true)
     */
    @Test
    fun test_01_todoPathNavigatesToAssistantTask() = runTest {
        /** Given / When **/
        val deeplink = Deeplink.parse(Uri.parse("black://navigate/todo"))

        /** Then **/
        assertEquals(Deeplink.Navigate(AppRoute.AssistantTask, clearBackStack = true), deeplink)
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
     * Then: black://navigate/todo, Navigate(AppRoute.AssistantTask, clearBackStack = true)
     */
    @Test
    fun test_03_navigateUriBuildsParsableDeeplink() = runTest {
        /** Given / When **/
        val uri = Deeplink.navigateUri(Deeplink.PathNavigate.TODO)

        /** Then **/
        assertEquals("black://navigate/todo", uri.toString())
        assertEquals(Deeplink.Navigate(AppRoute.AssistantTask, clearBackStack = true), Deeplink.parse(uri))
    }
}
