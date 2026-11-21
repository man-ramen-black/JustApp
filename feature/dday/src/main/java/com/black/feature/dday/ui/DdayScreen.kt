package com.black.feature.dday.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.ui.component.PermissionStatusRow
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography
import com.black.core.util.Extensions.activity
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import com.black.feature.dday.R
import com.black.feature.dday.data.usecase.DdayLoadResult
import com.black.feature.dday.data.usecase.limitTo
import com.black.feature.dday.model.DdayItem
import com.black.feature.dday.model.DdayStyle
import com.black.feature.dday.model.DdayStyleField
import com.black.feature.dday.model.ddayLabel
import com.black.feature.dday.model.formatEventDate
import com.black.feature.dday.widget.measureLabelWidth
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [DdayScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
object DdayTestTags {
    const val PERMISSION_ROW = "dday_permission_row"
    const val CALENDAR_LIST = "dday_calendar_list"
    const val PREVIEW = "dday_preview"

    fun calendarSwitch(calendarId: Long): String = "dday_calendar_switch_$calendarId"

    fun styleValue(field: DdayStyleField): String = "dday_style_${field.name.lowercase()}_value"

    fun styleIncrease(field: DdayStyleField): String = "dday_style_${field.name.lowercase()}_increase"

    fun styleDecrease(field: DdayStyleField): String = "dday_style_${field.name.lowercase()}_decrease"
}

/** 알파가 없는 캘린더 색(일부 동기화 어댑터)이 투명하게 그려지지 않도록 켜는 알파 비트 */
private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()

/** 미리보기 예시 일정이 오늘로부터 떨어진 날수 */
private val SAMPLE_DAY_OFFSETS = listOf(0L, 9L, 14L)

/** D-Day 위젯 설정 화면 */
@Composable
fun DdayScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DdayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 다시 묻지 않음으로 거부되면 시스템 요청 창이 뜨지 않으므로 앱 설정 화면으로 안내
    //
    // 요청 전에 근거 설명이 가능했는데(이미 한 번 거부) 거부 뒤 불가능해졌으면 다시 묻지 않음.
    // 근거 설명이 불가능한 채 거부된 경우는 첫 요청의 창 닫기일 수 있어 한 번 더 요청해 보고, 연속이면 다시 묻지 않음으로 봄.
    var settingsRequired by remember { mutableStateOf(false) }
    var rationaleBeforeRequest by remember { mutableStateOf(false) }
    var deniedWithoutRationale by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            settingsRequired = false
            deniedWithoutRationale = false
        } else if (canShowRationale(context)) {
            deniedWithoutRationale = false
        } else if (rationaleBeforeRequest || deniedWithoutRationale) {
            settingsRequired = true
            // 창 없이 연속 거부된 경우(진입 전부터 다시 묻지 않음 상태)는 탭에 반응이 없었으므로 바로 앱 설정을 엶
            if (!rationaleBeforeRequest) {
                PermissionHelper.showAppDetailsSettings(context)
            }
        } else {
            deniedWithoutRationale = true
        }
        viewModel.refresh()
    }

    // 첫 진입과 설정 앱에서 돌아왔을 때 권한과 캘린더 목록을 다시 읽음
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is DdayViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    DdayContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onRequestPermission = {
            if (settingsRequired) {
                PermissionHelper.showAppDetailsSettings(context)
            } else {
                rationaleBeforeRequest = canShowRationale(context)
                permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        },
        onCalendarIncludedChange = viewModel::setCalendarIncluded,
        onAdjustStyle = viewModel::adjustStyle,
        modifier = modifier,
    )
}

/** 캘린더 권한의 요청 근거 설명을 보여 줄 수 있는지(이미 한 번 거부했고 다시 묻지 않음은 아님) */
private fun canShowRationale(context: Context): Boolean {
    val activity = context.activity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
}

/**
 * D-Day 화면 콘텐츠
 *
 * 상태를 갖지 않고 받은 [uiState]와 콜백만으로 그리는 렌더링 전용.
 * 권한·기기 캘린더 목록은 기기 상태로 갈려 Screen으로는 한 분기만 실행되므로,
 * 앱 모듈의 계측 테스트가 상태를 주입해 분기마다 검증하도록 공개함.
 */
@Composable
fun DdayContent(
    uiState: DdayUiState,
    onNavigateUp: () -> Unit,
    onRequestPermission: () -> Unit,
    onCalendarIncludedChange: (Long, Boolean) -> Unit,
    onAdjustStyle: (DdayStyleField, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_dday),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    ) {
        // 첫 불러오기 전에는 권한 필요 상태가 잠깐 보이지 않도록 숨김
        if (uiState.loaded) {
            PermissionStatusRow(
                label = stringResource(R.string.dday_permission_label),
                granted = uiState.permissionGranted,
                onClick = onRequestPermission,
                modifier = Modifier.testTag(DdayTestTags.PERMISSION_ROW),
            )
        }
        if (uiState.loaded && uiState.permissionGranted) {
            SectionHeader(title = stringResource(R.string.dday_section_calendars))
            Column(modifier = Modifier.testTag(DdayTestTags.CALENDAR_LIST)) {
                uiState.calendars.forEach { option ->
                    CalendarRow(option = option, onCalendarIncludedChange = onCalendarIncludedChange)
                }
            }
        }
        SectionHeader(title = stringResource(R.string.dday_section_preview))
        DdayPreview(style = uiState.style, preview = uiState.preview)
        SectionHeader(title = stringResource(R.string.dday_section_display))
        StyleAdjustRow(field = DdayStyleField.ITEM_COUNT, style = uiState.style, onAdjustStyle = onAdjustStyle)
        SectionHeader(title = stringResource(R.string.dday_section_style))
        DdayStyleField.entries.filter { it != DdayStyleField.ITEM_COUNT }.forEach { field ->
            StyleAdjustRow(field = field, style = uiState.style, onAdjustStyle = onAdjustStyle)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CalendarRow(
    option: DdayCalendarOption,
    onCalendarIncludedChange: (Long, Boolean) -> Unit,
) {
    SwitchRow(
        label = option.calendar.displayName,
        checked = option.included,
        onCheckedChange = { onCalendarIncludedChange(option.calendar.id, it) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DdayTestTags.calendarSwitch(option.calendar.id)),
        leading = {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(option.calendar.color or OPAQUE_ALPHA_MASK))
                    // 검은색 캘린더도 검은 배경에서 점이 보이도록 테두리를 둠
                    .border(1.dp, AppColors.UncheckedControl, CircleShape),
            )
        },
        supportingText = option.calendar.accountName,
    )
}

/**
 * 위젯과 같은 배치·색·남은 날짜 칸 너비로 그리는 미리보기
 *
 * 실제 일정이 없거나 권한이 없으면 오늘 기준 D-Day·D-9·D-14 예시 일정을 보임.
 * 위젯처럼 설정한 일정 개수([DdayStyle.itemCount])만 보임.
 * 라벨은 위젯처럼 굵게 그려 [measureLabelWidth]로 잰 칸에 맞음. 위젯 본문은 Glance라 같은 Composable을 쓸 수 없어 일반 Compose로 따로 그림.
 * 위젯처럼 항목 묶음을 가로 가운데에 놓고, 가장 넓은 항목 너비(`IntrinsicSize.Max`)로 모든 항목 폭을 맞춰 남은 날짜 칸의 시작 위치를 맞춤.
 */
@Composable
private fun DdayPreview(style: DdayStyle, preview: DdayLoadResult) {
    val context = LocalContext.current
    val sampleItems = rememberSampleItems()
    val items = (preview.limitTo(style.itemCount) as? DdayLoadResult.Loaded)?.items?.takeIf { it.isNotEmpty() }
        ?: sampleItems.take(style.itemCount)
    val untitled = stringResource(R.string.dday_untitled)
    val labelWidth = remember(items, style.labelTextSize) {
        measureLabelWidth(context, items.map { it.label }, style.labelTextSize)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DdayTestTags.PREVIEW)
            .clip(AppShapes.NotificationPreview)
            .background(AppColors.ScreenBackground)
            .padding(style.padding.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(style.itemGap.dp),
        ) {
            items.forEach { item ->
                PreviewItemRow(style = style, item = item, labelWidth = labelWidth, untitled = untitled)
            }
        }
    }
}

@Composable
private fun PreviewItemRow(style: DdayStyle, item: DdayItem, labelWidth: Dp, untitled: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = item.label,
            modifier = Modifier.width(labelWidth),
            style = TextStyle(
                color = AppColors.Title,
                fontSize = style.labelTextSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(style.labelGap.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = item.title ?: untitled,
                style = TextStyle(color = AppColors.Title, fontSize = style.titleTextSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(style.titleDateGap.dp))
            BasicText(
                text = item.dateText,
                style = TextStyle(color = AppColors.SubText, fontSize = style.dateTextSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 오늘 기준 D-Day·D-9·D-14 예시 일정 */
@Composable
private fun rememberSampleItems(): List<DdayItem> {
    val titles = listOf(
        stringResource(R.string.dday_sample_title_1),
        stringResource(R.string.dday_sample_title_2),
        stringResource(R.string.dday_sample_title_3),
    )
    // 자정을 넘기면 오늘이 달라지므로 그릴 때마다 구하고, 날짜가 같으면 예시 목록을 재사용
    val today = LocalDate.now()
    return remember(titles, today) {
        SAMPLE_DAY_OFFSETS.mapIndexed { index, offset ->
            val date = today.plusDays(offset)
            val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            DdayItem(
                eventId = -(index + 1L),
                title = titles[index],
                label = ddayLabel(date, today),
                dateText = formatEventDate(begin, true, ZoneId.systemDefault()),
                begin = begin,
                end = begin,
            )
        }
    }
}

@Composable
private fun StyleAdjustRow(
    field: DdayStyleField,
    style: DdayStyle,
    onAdjustStyle: (DdayStyleField, Int) -> Unit,
) {
    val name = stringResource(field.nameResId())
    val value = style[field]
    val unitResId = field.unitResId()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = AppTypography.ItemName,
        )
        StepButton(
            iconResId = R.drawable.dday_ic_remove,
            contentDescription = stringResource(R.string.dday_style_decrease, name),
            enabled = value > field.min,
            onClick = { onAdjustStyle(field, -1) },
            modifier = Modifier.testTag(DdayTestTags.styleDecrease(field)),
        )
        Text(
            text = stringResource(unitResId, value),
            modifier = Modifier
                .width(56.dp)
                .testTag(DdayTestTags.styleValue(field)),
            style = AppTypography.Body,
            textAlign = TextAlign.Center,
        )
        StepButton(
            iconResId = R.drawable.dday_ic_add,
            contentDescription = stringResource(R.string.dday_style_increase, name),
            enabled = value < field.max,
            onClick = { onAdjustStyle(field, 1) },
            modifier = Modifier.testTag(DdayTestTags.styleIncrease(field)),
        )
    }
}

@Composable
private fun StepButton(
    @DrawableRes iconResId: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.3f),
            tint = AppColors.Title,
        )
    }
}

@StringRes
private fun DdayStyleField.unitResId(): Int = when (this) {
    DdayStyleField.LABEL_TEXT_SIZE,
    DdayStyleField.TITLE_TEXT_SIZE,
    DdayStyleField.DATE_TEXT_SIZE,
    -> R.string.dday_unit_sp

    DdayStyleField.LABEL_GAP,
    DdayStyleField.TITLE_DATE_GAP,
    DdayStyleField.ITEM_GAP,
    DdayStyleField.PADDING,
    -> R.string.dday_unit_dp

    DdayStyleField.ITEM_COUNT -> R.string.dday_unit_count
}

@StringRes
private fun DdayStyleField.nameResId(): Int = when (this) {
    DdayStyleField.LABEL_TEXT_SIZE -> R.string.dday_style_label_text_size
    DdayStyleField.TITLE_TEXT_SIZE -> R.string.dday_style_title_text_size
    DdayStyleField.DATE_TEXT_SIZE -> R.string.dday_style_date_text_size
    DdayStyleField.LABEL_GAP -> R.string.dday_style_label_gap
    DdayStyleField.TITLE_DATE_GAP -> R.string.dday_style_title_date_gap
    DdayStyleField.ITEM_GAP -> R.string.dday_style_item_gap
    DdayStyleField.PADDING -> R.string.dday_style_padding
    DdayStyleField.ITEM_COUNT -> R.string.dday_style_item_count
}
