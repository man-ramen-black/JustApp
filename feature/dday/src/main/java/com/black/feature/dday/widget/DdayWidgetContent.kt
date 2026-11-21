package com.black.feature.dday.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.black.core.ui.theme.AppColors
import com.black.core.util.Extensions.appLaunchIntent
import com.black.feature.dday.R
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle

/**
 * D-Day 설정 화면으로 이동하는 앱 딥링크
 *
 * 앱의 `Deeplink`가 해석하는 `black://navigate/dday`이며, 기능 모듈은 앱 모듈을 참조할 수 없어 문자열로 둠.
 */
val DDAY_SETTINGS_DEEPLINK_URI: Uri = Uri.parse("black://navigate/dday")

/**
 * 설정 화면을 여는 인텐트
 *
 * black 스킴 인텐트 필터에는 DEFAULT 카테고리가 없어 인텐트 필터 해석에서 걸러지므로, 앱 시작 액티비티를 지정한 명시적 인텐트로 엶.
 * 시작 액티비티를 찾지 못하면 패키지만 지정함.
 */
fun ddaySettingsIntent(context: Context): Intent {
    val launcher = context.appLaunchIntent()?.component
    val intent = Intent(Intent.ACTION_VIEW, DDAY_SETTINGS_DEEPLINK_URI)
    return if (launcher != null) {
        intent.setComponent(launcher)
    } else {
        intent.setPackage(context.packageName)
    }
}

/** [item]의 캘린더 일정 상세를 여는 인텐트 */
fun calendarEventIntent(item: DdayItem): Intent {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.eventId)
    return Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, item.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, item.end)
}

/**
 * D-Day 위젯 본문
 *
 * 상태만 받아 그리므로 조회와 설정 읽기는 호출하는 쪽에서 끝냄. [labelWidth]는 [measureLabelWidth]로 잰 남은 날짜 칸 너비.
 * 위젯 색은 설정과 무관한 고정값이며 `AppColors`의 배경(`ScreenBackground`)·제목(`Title`)·보조 문구(`SubText`) 색을 씀.
 * 글꼴은 [WIDGET_FONT_FAMILY]로 고정함. 지정하지 않으면 런처 테마 글꼴로 그려져 [measureLabelWidth]로 잰 칸보다 라벨이 넓어지면서 `D-Day`가 잘림.
 */
@Composable
fun DdayWidgetContent(style: DdayStyle, result: DdayLoadResult, labelWidth: Dp) {
    val context = LocalContext.current
    var rootModifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(AppColors.ScreenBackground))
        .padding(style.padding.dp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rootModifier = rootModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    }
    Box(modifier = rootModifier) {
        when (result) {
            DdayLoadResult.NoPermission -> MessageText(
                text = context.getString(R.string.dday_permission_required),
                style = style,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(ddaySettingsIntent(context))),
            )

            DdayLoadResult.Failed -> MessageText(
                text = context.getString(R.string.dday_load_failed),
                style = style,
                modifier = GlanceModifier.fillMaxSize(),
            )

            is DdayLoadResult.Loaded -> if (result.items.isEmpty()) {
                MessageText(
                    text = context.getString(R.string.dday_empty),
                    style = style,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            } else {
                ItemList(style = style, items = result.items, labelWidth = labelWidth)
            }
        }
    }
}

/** 위젯 글자를 그리는 글꼴, [measureLabelWidth]가 재는 글꼴과 같아야 함 */
private val WIDGET_FONT_FAMILY = FontFamily.SansSerif

@Composable
private fun MessageText(text: String, style: DdayStyle, modifier: GlanceModifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(AppColors.Title),
                fontSize = style.titleTextSize.sp,
                textAlign = TextAlign.Center,
                fontFamily = WIDGET_FONT_FAMILY,
            ),
        )
    }
}

@Composable
private fun ItemList(style: DdayStyle, items: List<DdayItem>, labelWidth: Dp) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        itemsIndexed(items) { index, item ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.height(style.itemGap.dp))
                }
                ItemRow(style = style, item = item, labelWidth = labelWidth)
            }
        }
    }
}

@Composable
private fun ItemRow(style: DdayStyle, item: DdayItem, labelWidth: Dp) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(calendarEventIntent(item))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            modifier = GlanceModifier.width(labelWidth),
            style = TextStyle(
                color = ColorProvider(AppColors.Title),
                fontSize = style.labelTextSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = WIDGET_FONT_FAMILY,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(style.labelGap.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title ?: context.getString(R.string.dday_untitled),
                style = TextStyle(
                    color = ColorProvider(AppColors.Title),
                    fontSize = style.titleTextSize.sp,
                    fontFamily = WIDGET_FONT_FAMILY,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(style.titleDateGap.dp))
            Text(
                text = item.dateText,
                style = TextStyle(
                    color = ColorProvider(AppColors.SubText),
                    fontSize = style.dateTextSize.sp,
                    fontFamily = WIDGET_FONT_FAMILY,
                ),
                maxLines = 1,
            )
        }
    }
}
