package com.black.feature.dday.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import com.black.core.util.runSuspendCatching
import com.black.feature.dday.data.DdayRepository
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.data.usecase.LoadDdayItemsUseCase
import com.black.feature.dday.data.usecase.limitTo
import com.black.feature.dday.model.DdayStyle

/** 위젯에 그릴 일정 조회 결과, 스타일, 남은 날짜 칸 너비와 제목 칸 너비를 정하는 가장 넓은 제목·날짜 묶음 */
data class DdayWidgetData(
    val style: DdayStyle,
    val result: DdayLoadResult,
    val labelWidth: Dp,
    val widestText: DdayWidestText,
)

/**
 * 캘린더 일정까지 남은 날짜를 보여 주는 홈 화면 위젯
 *
 * 위젯은 Hilt 주입을 쓰지 않고 [Context]로 저장소와 조회 유스케이스를 직접 만듦.
 * 새 세션의 첫 화면은 `provideContent` 앞에서 읽은 데이터로 그리고, 그 뒤의 일정과 스타일은 [DdayWidgetRoot]가 읽음.
 * Glance의 `IgnoreResult`는 공개 API가 아니라 쓸 수 없고, 데이터 없이 빈 화면을 올리면 새 세션마다 위젯이 투명하게 깜빡이므로
 * 첫 화면에 쓸 데이터를 미리 읽어 둠.
 * Glance는 세션이 살아 있는 동안(처음 45초, 이후 유휴 5초) `update()`가 `provideGlance`를 다시 부르지 않고
 * 상태만 바꿔 다시 구성하므로, `provideGlance`에서 한 번 읽으면 그 사이의 갱신이 화면에 반영되지 않음.
 */
class DdayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialData = loadDdayWidgetData(context)
        provideContent {
            DdayWidgetRoot(initialData = initialData, loadData = { loadDdayWidgetData(context) })
        }
    }
}

/**
 * 위젯별 상태의 갱신 토큰 키
 *
 * [DdayWidgetRefresher.refresh]가 값을 새로 써서 세션 중에도 [DdayWidgetRoot]가 다시 읽게 함.
 */
val DDAY_REFRESH_TOKEN_KEY = stringPreferencesKey("dday_refresh_token")

/**
 * 일정·스타일 조회와 남은 날짜 칸 너비 측정, 가장 넓은 제목·날짜 고르기
 *
 * 조회나 설정 읽기가 실패해도 위젯이 죽지 않도록 조회 실패 화면과 기본 스타일로 대체함.
 * 일정은 설정한 개수([DdayStyle.itemCount])만 남기고 그 항목으로 칸 너비를 잼.
 */
suspend fun loadDdayWidgetData(context: Context): DdayWidgetData {
    val style = runSuspendCatching { DdayRepository(context.applicationContext).getStyle() }
        .getOrDefault(DdayStyle())
    val result = runSuspendCatching { LoadDdayItemsUseCase(context).load() }
        .getOrDefault(DdayLoadResult.Failed)
        .limitTo(style.itemCount)
    val items = (result as? DdayLoadResult.Loaded)?.items.orEmpty()
    return DdayWidgetData(
        style = style,
        result = result,
        labelWidth = measureLabelWidth(context, items.map { it.label }, style.labelTextSize),
        widestText = findWidestText(context, items, style),
    )
}

/**
 * 위젯 상태의 갱신 토큰을 읽어 [DdayWidgetLoader]에 넘기는 위젯 뿌리
 *
 * Glance 단위 테스트는 구성 뒤에 상태를 바꿀 수 없어, 토큰을 받는 [DdayWidgetLoader]를 따로 둠.
 */
@Composable
fun DdayWidgetRoot(initialData: DdayWidgetData, loadData: suspend () -> DdayWidgetData) {
    val token = currentState<Preferences>()[DDAY_REFRESH_TOKEN_KEY]
    DdayWidgetLoader(token = token, initialData = initialData, loadData = loadData)
}

/**
 * [initialData]를 먼저 그리고, [token]이 바뀌거나 처음 구성될 때마다 [loadData]로 다시 읽어 [DdayWidgetContent]를 그림
 *
 * 처음 구성에서도 다시 읽는 것은 [initialData]를 읽은 뒤 구성되기 전에 바뀐 토큰을 놓치지 않기 위함.
 * 다시 읽는 동안에는 이전 데이터를 그대로 두고, 앞선 조회가 끝나기 전에 토큰이 또 바뀌면 앞선 조회를 취소함.
 */
@Composable
fun DdayWidgetLoader(token: String?, initialData: DdayWidgetData, loadData: suspend () -> DdayWidgetData) {
    val data by produceState(initialValue = initialData, token) {
        value = loadData()
    }
    DdayWidgetContent(style = data.style, result = data.result, labelWidth = data.labelWidth, widestText = data.widestText)
}
