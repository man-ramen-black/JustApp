package com.black.feature.dday.widget

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle
import com.black.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [DdayWidgetRoot]의 첫 프레임과 갱신 토큰 변경 때 재조회 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DdayWidgetRootTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun dataOf(title: String) = DdayWidgetData(
        style = DdayStyle(),
        result = DdayLoadResult.Loaded(
            listOf(DdayItem(eventId = 1L, title = title, label = "D-1", dateText = "9/4 Sun", begin = 1L, end = 2L)),
        ),
        labelWidth = 30.dp,
        widestText = DdayWidestText(title = "가장 넓은 제목", date = "가장 넓은 날짜"),
    )

    private fun tokenState(token: String): Preferences = preferencesOf(DDAY_REFRESH_TOKEN_KEY to token)

    /**
     * 처음 구성에서 조회 결과를 그리는지 검증
     *
     * Given: 토큰 a와 제목 "첫째"를 돌려주는 조회
     * When: 위젯 뿌리를 그림
     * Then: "첫째" 노드가 존재
     */
    @Test
    fun test_01_firstCompositionLoadsData() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)
        setState(tokenState("a"))

        /** When **/
        provideComposable { DdayWidgetRoot(initialData = dataOf("첫째"), loadData = { dataOf("첫째") }) }
        awaitIdle()

        /** Then **/
        onNode(hasText("첫째")).assertExists()
    }

    /**
     * 조회가 끝나기 전에도 미리 읽은 데이터로 첫 화면을 그리는지 검증
     *
     * Given: 미리 읽은 제목 "초기"와 끝나지 않는 조회
     * When: 위젯 로더를 그리고 유휴까지 대기
     * Then: "초기" 노드가 존재
     */
    @Test
    fun test_03_pendingLoadKeepsInitialData() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)
        val neverLoaded = CompletableDeferred<DdayWidgetData>()

        /** When **/
        provideComposable {
            DdayWidgetLoader(token = "a", initialData = dataOf("초기"), loadData = { neverLoaded.await() })
        }
        awaitIdle()

        /** Then **/
        onNode(hasText("초기")).assertExists()
    }

    /**
     * 같은 구성 안에서 갱신 토큰이 바뀌면 다시 조회해 화면이 바뀌는지 검증
     *
     * Given: 첫 조회 중에 토큰이 a에서 b로 바뀌고 조회마다 다른 제목을 돌려주는 구성, 미리 읽은 제목은 "초기"
     * When: 위젯 로더를 그리고 유휴까지 대기
     * Then: 조회가 두 번 일어나고 두 번째 제목만 표시됨
     */
    @Test
    fun test_02_tokenChangeReloadsInSameComposition() = runGlanceAppWidgetUnitTest {
        /** Given **/
        setContext(context)
        var loadCount = 0
        val token = mutableStateOf("a")

        /** When **/
        provideComposable {
            DdayWidgetLoader(
                token = token.value,
                initialData = dataOf("초기"),
                loadData = {
                    loadCount++
                    if (loadCount == 1) {
                        token.value = "b"
                    }
                    dataOf(if (loadCount == 1) "첫째" else "둘째")
                },
            )
        }
        awaitIdle()

        /** Then **/
        assertEquals(2, loadCount)
        onNode(hasText("둘째")).assertExists()
        onNode(hasText("초기")).assertDoesNotExist()
        onNode(hasText("첫째")).assertDoesNotExist()
    }
}
