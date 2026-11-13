package com.black.app.ui.main.usagelog

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.R
import com.black.app.usagelog.collect.UsageLogListenerService
import com.black.app.usagelog.record.AppUsageRecord
import com.black.app.usagelog.record.GapCause
import com.black.app.usagelog.record.GapRecord
import com.black.app.usagelog.record.NotificationPostedRecord
import com.black.app.usagelog.record.NotificationRemovedRecord
import com.black.app.usagelog.record.RemovalReason
import com.black.app.usagelog.record.ScreenOffRecord
import com.black.app.usagelog.record.ScreenOnRecord
import com.black.app.usagelog.record.UnlockRecord
import com.black.app.usagelog.record.WifiConnectedRecord
import com.black.app.usagelog.record.WifiDisconnectedRecord
import com.black.app.usagelog.record.WifiRecord
import com.black.app.usagelog.upload.DiscordWebhookFormat
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.ConfirmDialog
import com.black.core.ui.component.ContainerIconButton
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.StatusRow
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.util.DataUtil
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [UsageLogScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object UsageLogTestTags {
    const val TITLE = "usage_log_title"
    const val COLLECTION_SWITCH = "usage_log_collection_switch"
    const val USAGE_ACCESS_STATUS = "usage_log_usage_access_status"
    const val NOTIFICATION_ACCESS_STATUS = "usage_log_notification_access_status"
    const val LOCATION_STATUS = "usage_log_location_status"
    const val BATTERY_STATUS = "usage_log_battery_status"
    const val REGISTER_WIFI_BUTTON = "usage_log_register_wifi_button"
    const val EMPTY_HOME_WIFI = "usage_log_empty_home_wifi"
    const val EMPTY_RECORDS = "usage_log_empty_records"
    const val RECENT_RECORDS = "usage_log_recent_records"
    const val SEND_SECTION = "usage_log_send_section"
    const val WEBHOOK_URL_FIELD = "usage_log_webhook_url_field"
    const val ENCRYPTION_KEY = "usage_log_encryption_key"
    const val COPY_KEY_BUTTON = "usage_log_copy_key_button"
    const val REGENERATE_KEY_BUTTON = "usage_log_regenerate_key_button"
    const val REGENERATE_KEY_DIALOG = "usage_log_regenerate_key_dialog"
    const val REGENERATE_KEY_CONFIRM = "usage_log_regenerate_key_confirm"
    const val LAST_SEND_STATUS = "usage_log_last_send_status"
    const val SEND_NOW_BUTTON = "usage_log_send_now_button"

    fun homeWifiDeleteButton(ssid: String): String = "usage_log_home_wifi_delete_$ssid"
}

/** 기기 사용 기록 화면 */
@Composable
fun UsageLogScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 설정 앱·권한 창에서 돌아온 경우의 상태 갱신과 수집
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is UsageLogViewModel.EventOpenUsageAccessSettings -> {
                PermissionHelper.showUsageAccessSettings(context)
            }
            is UsageLogViewModel.EventOpenNotificationAccessSettings -> {
                PermissionHelper.showNotificationListenerSettings(context, UsageLogListenerService::class.java)
            }
            is UsageLogViewModel.EventOpenLocationPermissionSettings -> {
                PermissionHelper.showAppDetailsSettings(context)
            }
            is UsageLogViewModel.EventOpenLocationSourceSettings -> {
                PermissionHelper.showLocationSourceSettings(context)
            }
            is UsageLogViewModel.EventOpenBatteryOptimizationSettings -> {
                PermissionHelper.showIgnoreBatteryOptimizationSettings(context, withToast = true)
            }
            is UsageLogViewModel.EventCopyEncryptionKey -> {
                copyEncryptionKey(context, viewModel.uiState.value.encryptionKey)
            }
            is UsageLogViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    UsageLogContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onCollectionEnabledChange = viewModel::setCollectionEnabled,
        onClickUsageAccess = viewModel::openUsageAccessSettings,
        onClickNotificationAccess = viewModel::openNotificationAccessSettings,
        onClickLocation = viewModel::openLocationSettings,
        onClickBatteryOptimization = viewModel::openBatteryOptimizationSettings,
        onClickRegisterWifi = viewModel::registerCurrentWifi,
        onClickDeleteWifi = viewModel::removeHomeWifi,
        onWebhookUrlChange = viewModel::updateWebhookUrl,
        onClickCopyKey = viewModel::copyEncryptionKey,
        onClickRegenerateKey = viewModel::requestRegenerateKey,
        onConfirmRegenerateKey = viewModel::confirmRegenerateKey,
        onDismissRegenerateKey = viewModel::dismissRegenerateKey,
        onClickSendNow = viewModel::sendNow,
    )
}

/**
 * 암호화 키를 클립보드에 복사
 *
 * 클립보드 미리보기에 키가 보이지 않도록 민감 정보 표시를 붙임.
 * API 33 이상은 시스템이 복사 확인을 직접 보여 주므로 API 32 이하에서만 복사 토스트를 띄움.
 */
@SuppressLint("InlinedApi")
private fun copyEncryptionKey(context: Context, key: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(null, key)
    // EXTRA_IS_SENSITIVE는 컴파일 때 문자열 값으로 들어가 API 33 미만에서도 버전 분기 없이 쓸 수 있음
    clipData.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    clipboardManager.setPrimaryClip(clipData)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.usage_log_key_copied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 기기 사용 기록 화면 콘텐츠(상태 무관 렌더링 전용)
 *
 * 계측 테스트가 상태를 주입해 기기의 권한 상태와 무관하게 권한 행의 분기마다 검증하도록 private 대신 internal
 */
@Composable
internal fun UsageLogContent(
    uiState: UsageLogUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onCollectionEnabledChange: (Boolean) -> Unit,
    onClickUsageAccess: () -> Unit,
    onClickNotificationAccess: () -> Unit,
    onClickLocation: () -> Unit,
    onClickBatteryOptimization: () -> Unit,
    onClickRegisterWifi: () -> Unit,
    onClickDeleteWifi: (String) -> Unit,
    onWebhookUrlChange: (String) -> Unit,
    onClickCopyKey: () -> Unit,
    onClickRegenerateKey: () -> Unit,
    onConfirmRegenerateKey: () -> Unit,
    onDismissRegenerateKey: () -> Unit,
    onClickSendNow: () -> Unit,
) {
    val locationRequiredResId = locationRequiredStatusResId(uiState)
    // 기록 시각 형식은 목록 전체가 하나를 공유
    val timeFormat = remember { SimpleDateFormat(RECORD_TIME_PATTERN, Locale.getDefault()) }
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_usage_log),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(UsageLogTestTags.TITLE),
    ) {
        // 수집 섹션
        SectionHeader(title = stringResource(R.string.usage_log_section_collection))
        SwitchRow(
            label = stringResource(R.string.usage_log_switch_label),
            checked = uiState.collectionEnabled,
            onCheckedChange = onCollectionEnabledChange,
            modifier = Modifier.testTag(UsageLogTestTags.COLLECTION_SWITCH),
        )

        // 권한 섹션
        SectionHeader(title = stringResource(R.string.usage_log_section_permission))
        PermissionRow(
            labelResId = R.string.usage_log_permission_usage_access,
            granted = uiState.usageAccessGranted,
            onClick = onClickUsageAccess,
            testTag = UsageLogTestTags.USAGE_ACCESS_STATUS,
        )
        PermissionRow(
            labelResId = R.string.usage_log_permission_notification_access,
            granted = uiState.notificationAccessGranted,
            onClick = onClickNotificationAccess,
            testTag = UsageLogTestTags.NOTIFICATION_ACCESS_STATUS,
        )
        PermissionRow(
            labelResId = R.string.usage_log_permission_location,
            granted = locationRequiredResId == null,
            onClick = onClickLocation,
            testTag = UsageLogTestTags.LOCATION_STATUS,
            requiredValueResId = locationRequiredResId ?: R.string.usage_log_status_required,
        )
        PermissionRow(
            labelResId = R.string.usage_log_permission_battery,
            granted = uiState.batteryOptimizationIgnored,
            onClick = onClickBatteryOptimization,
            testTag = UsageLogTestTags.BATTERY_STATUS,
        )

        // 집 Wi-Fi 섹션
        SectionHeader(title = stringResource(R.string.usage_log_section_home_wifi))
        if (uiState.homeSsids.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_log_empty_home_wifi),
                modifier = Modifier.testTag(UsageLogTestTags.EMPTY_HOME_WIFI),
                style = AppTypography.Body,
            )
        } else {
            uiState.homeSsids.forEach { ssid ->
                HomeWifiRow(ssid = ssid, onClickDelete = { onClickDeleteWifi(ssid) })
            }
        }
        ActionButton(
            text = uiState.currentSsid
                ?.let { stringResource(R.string.usage_log_register_wifi, it) }
                ?: stringResource(R.string.usage_log_register_wifi_unknown),
            onClick = onClickRegisterWifi,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag(UsageLogTestTags.REGISTER_WIFI_BUTTON),
            letterSpacing = 0.sp,
            // 이름을 읽지 못한 상태는 눌렀을 때 안내 토스트를 보여야 해서 활성 유지
            enabled = uiState.currentSsid == null || uiState.currentSsid !in uiState.homeSsids,
        )

        // 전송 섹션
        SendSection(
            uiState = uiState,
            timeFormat = timeFormat,
            onWebhookUrlChange = onWebhookUrlChange,
            onClickCopyKey = onClickCopyKey,
            onClickRegenerateKey = onClickRegenerateKey,
            onClickSendNow = onClickSendNow,
        )

        // 최근 기록 섹션
        SectionHeader(title = stringResource(R.string.usage_log_section_recent))
        if (uiState.recentRecords.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_log_empty_records),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag(UsageLogTestTags.EMPTY_RECORDS),
                style = AppTypography.Body,
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag(UsageLogTestTags.RECENT_RECORDS),
            ) {
                uiState.recentRecords.forEach { item ->
                    key(item.id) {
                        RecordRow(item = item, timeFormat = timeFormat)
                    }
                }
            }
        }
    }

    if (uiState.showRegenerateKeyConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.usage_log_regenerate_key_message),
            confirmText = stringResource(R.string.usage_log_regenerate_key),
            onConfirm = onConfirmRegenerateKey,
            onDismiss = onDismissRegenerateKey,
            modifier = Modifier.testTag(UsageLogTestTags.REGENERATE_KEY_DIALOG),
            confirmButtonModifier = Modifier.testTag(UsageLogTestTags.REGENERATE_KEY_CONFIRM),
        )
    }
}

/**
 * 전송 섹션
 *
 * 웹후크 주소, 암호화 키, 마지막 전송 상태, 지금 보내기 버튼을 보여 줌.
 * 암호화 키는 앞뒤 일부만 남겨 보여 주며 전체 값은 복사로만 꺼냄.
 */
@Composable
private fun SendSection(
    uiState: UsageLogUiState,
    timeFormat: SimpleDateFormat,
    onWebhookUrlChange: (String) -> Unit,
    onClickCopyKey: () -> Unit,
    onClickRegenerateKey: () -> Unit,
    onClickSendNow: () -> Unit,
) {
    val lastSendResult = uiState.lastSendResult
    Column(modifier = Modifier.testTag(UsageLogTestTags.SEND_SECTION)) {
        SectionHeader(title = stringResource(R.string.usage_log_section_send))
        WebhookUrlField(savedUrl = uiState.webhookUrl, onUrlChange = onWebhookUrlChange)
        StatusRow(
            label = stringResource(R.string.usage_log_encryption_key_label),
            value = if (uiState.encryptionKey.isEmpty()) {
                stringResource(R.string.usage_log_key_none)
            } else {
                maskKey(uiState.encryptionKey)
            },
            onClick = null,
            modifier = Modifier.testTag(UsageLogTestTags.ENCRYPTION_KEY),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = stringResource(R.string.usage_log_copy_key),
                onClick = onClickCopyKey,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UsageLogTestTags.COPY_KEY_BUTTON),
                letterSpacing = 0.sp,
                enabled = uiState.encryptionKey.isNotEmpty(),
            )
            ActionButton(
                text = stringResource(R.string.usage_log_regenerate_key),
                onClick = onClickRegenerateKey,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UsageLogTestTags.REGENERATE_KEY_BUTTON),
                letterSpacing = 0.sp,
            )
        }
        StatusRow(
            label = stringResource(R.string.usage_log_last_send_label),
            value = if (lastSendResult == null) {
                stringResource(R.string.usage_log_last_send_none, uiState.pendingCount)
            } else {
                stringResource(
                    R.string.usage_log_last_send_value,
                    timeFormat.format(Date(lastSendResult.time)),
                    stringResource(if (lastSendResult.success) R.string.usage_log_send_success else R.string.usage_log_send_failure),
                    uiState.pendingCount,
                )
            },
            onClick = null,
            modifier = Modifier.testTag(UsageLogTestTags.LAST_SEND_STATUS),
        )
        ActionButton(
            text = stringResource(R.string.usage_log_send_now),
            onClick = onClickSendNow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag(UsageLogTestTags.SEND_NOW_BUTTON),
            letterSpacing = 0.sp,
        )
    }
}

/**
 * 웹후크 주소 입력 칸
 *
 * 저장소 값의 비동기 왕복이 커서 이동과 글자 누락을 일으키지 않도록 입력 중인 글자는 이 칸의 상태로 들고, 입력마다 [onUrlChange]로 저장함.
 * 사용자가 입력하기 전에 저장된 값이 도착하면 그 값으로 채움.
 * 비어 있지 않은 값이 올바르지 않을 때만 오류 표시를 켬.
 */
@Composable
private fun WebhookUrlField(
    savedUrl: String,
    onUrlChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(savedUrl) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(savedUrl) {
        if (!edited) text = savedUrl
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            edited = true
            text = it
            onUrlChange(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UsageLogTestTags.WEBHOOK_URL_FIELD),
        textStyle = AppTypography.Body.copy(color = AppColors.ItemName),
        label = { Text(text = stringResource(R.string.usage_log_webhook_url_label), style = AppTypography.Caption) },
        isError = text.isNotBlank() && !DiscordWebhookFormat.isValidWebhookUrl(text.trim()),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = AppColors.ContainerBorder,
            focusedBorderColor = AppColors.Arrow,
            // AppColors에 오류 색이 없어 오류 커서와 같은 테마 오류 색 사용
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedTextColor = AppColors.ItemName,
            unfocusedTextColor = AppColors.ItemName,
            errorTextColor = AppColors.ItemName,
            cursorColor = AppColors.Title,
            focusedLabelColor = AppColors.Caption,
            unfocusedLabelColor = AppColors.Caption,
            errorLabelColor = AppColors.Caption,
        ),
    )
}

/** 키의 앞 4자와 뒤 3자만 남기고 가린 표시 */
private fun maskKey(key: String): String =
    if (key.length <= MASK_VISIBLE_HEAD + MASK_VISIBLE_TAIL) {
        key
    } else {
        "${key.take(MASK_VISIBLE_HEAD)}…${key.takeLast(MASK_VISIBLE_TAIL)}"
    }

/**
 * 권한 상태 행
 *
 * 허용됨이면 누를 수 없고, 아니면 [requiredValueResId] 상태값을 보여 주며 눌러 조치 화면으로 이동.
 */
@Composable
private fun PermissionRow(
    @StringRes labelResId: Int,
    granted: Boolean,
    onClick: () -> Unit,
    testTag: String,
    @StringRes requiredValueResId: Int = R.string.usage_log_status_required,
) {
    StatusRow(
        label = stringResource(labelResId),
        value = stringResource(if (granted) R.string.usage_log_status_allowed else requiredValueResId),
        onClick = if (granted) null else onClick,
        modifier = Modifier.testTag(testTag),
    )
}

/**
 * 위치 행에 보여 줄 조치 필요 상태값
 *
 * 권한과 기기 위치 설정이 모두 갖춰졌으면 null.
 * 권한을 먼저 안내하고, 권한이 모두 있을 때만 기기 위치 설정 꺼짐을 안내함.
 * API 31 이상에서 대략적인 위치만 허용했으면 앱 정보 화면에서 정확한 위치를 켜야 하므로 따로 표시함.
 */
@StringRes
private fun locationRequiredStatusResId(uiState: UsageLogUiState): Int? = when {
    !uiState.fineLocationGranted && uiState.approximateLocationOnly -> R.string.usage_log_status_precise_location_required
    !uiState.fineLocationGranted || !uiState.backgroundLocationGranted -> R.string.usage_log_status_required
    !uiState.locationEnabled -> R.string.usage_log_status_location_off
    else -> null
}

/** 등록된 집 Wi-Fi 이름과 삭제 버튼 */
@Composable
private fun HomeWifiRow(
    ssid: String,
    onClickDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ssid,
            modifier = Modifier.weight(1f),
            style = AppTypography.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ContainerIconButton(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.usage_log_delete_wifi, ssid),
            onClick = onClickDelete,
            modifier = Modifier.testTag(UsageLogTestTags.homeWifiDeleteButton(ssid)),
            boxSize = 36.dp,
            iconSize = 16.dp,
        )
    }
}

/** 최근 기록 한 줄로 시각과 종류별 내용 표시 */
@Composable
private fun RecordRow(
    item: UsageLogRecordItem,
    timeFormat: SimpleDateFormat,
) {
    val timeText = remember(item.record.time) { timeFormat.format(Date(item.record.time)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = timeText,
            style = AppTypography.Caption,
        )
        Text(
            text = recordDescription(item, timeFormat),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = AppTypography.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 기록 종류별 한 줄 설명
 *
 * 최근 기록은 사용자가 수집 결과를 폰에서 바로 확인하는 용도라 알림 제목·본문과 Wi-Fi 이름, 집 여부도 그대로 표시함.
 */
@Composable
private fun recordDescription(
    item: UsageLogRecordItem,
    timeFormat: SimpleDateFormat,
): String = when (val record = item.record) {
    is AppUsageRecord -> stringResource(
        R.string.usage_log_record_app_usage,
        record.appName,
        DataUtil.milliSecondsToTimeString("H:mm:ss", (record.end - record.start).coerceAtLeast(0L)),
    )
    is ScreenOnRecord -> stringResource(R.string.usage_log_record_screen_on)
    is ScreenOffRecord -> stringResource(R.string.usage_log_record_screen_off)
    is UnlockRecord -> stringResource(R.string.usage_log_record_unlock)
    is NotificationPostedRecord -> listOfNotNull(record.appName, record.title, record.text).joinToString(RECORD_SEPARATOR)
    is NotificationRemovedRecord -> stringResource(
        R.string.usage_log_record_notification_removed,
        item.appLabel ?: record.packageName,
        stringResource(removalReasonResId(record.reason)),
    )
    is WifiConnectedRecord -> stringResource(R.string.usage_log_record_wifi_connected, wifiDescription(record))
    is WifiDisconnectedRecord -> stringResource(R.string.usage_log_record_wifi_disconnected, wifiDescription(record))
    is GapRecord -> stringResource(
        R.string.usage_log_record_gap,
        stringResource(gapCauseResId(record.cause)),
        timeFormat.format(Date(record.end)),
    )
}

/**
 * Wi-Fi 기록 설명
 *
 * Wi-Fi 이름 뒤에 집이면 (집), 추정이면 (추정)을 이어 붙이며, 이름을 모르면 이름 대신 안내 문구를 씀.
 */
@Composable
private fun wifiDescription(record: WifiRecord): String = listOfNotNull(
    record.ssid ?: stringResource(R.string.usage_log_wifi_name_unknown),
    if (record.home == true) stringResource(R.string.usage_log_record_home) else null,
    if (record.inferred) stringResource(R.string.usage_log_record_inferred) else null,
).joinToString(" ")

@StringRes
private fun removalReasonResId(reason: RemovalReason): Int = when (reason) {
    RemovalReason.CLICK -> R.string.usage_log_removal_click
    RemovalReason.DISMISS -> R.string.usage_log_removal_dismiss
    RemovalReason.APP_CANCEL -> R.string.usage_log_removal_app_cancel
    RemovalReason.OTHER -> R.string.usage_log_removal_other
}

@StringRes
private fun gapCauseResId(cause: GapCause): Int = when (cause) {
    GapCause.COLLECTION_OFF -> R.string.usage_log_gap_collection_off
    GapCause.USAGE_EVENTS_EXPIRED -> R.string.usage_log_gap_usage_events_expired
    GapCause.UNSENT_DELETED -> R.string.usage_log_gap_unsent_deleted
}

private const val RECORD_TIME_PATTERN = "MM-dd HH:mm"
private const val RECORD_SEPARATOR = " · "
private const val MASK_VISIBLE_HEAD = 4
private const val MASK_VISIBLE_TAIL = 3
