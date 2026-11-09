package com.black.app.ui.maintab.main.usagetimer

import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.R
import com.black.app.ui.common.selectapp.SelectAppDialogFragment
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.util.DataUtil
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents
import kotlinx.coroutines.delay

/** UsageTimer 화면 */
@Composable
fun UsageTimerScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageTimerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // SelectAppDialog 결과 관찰 (AppCompatActivity의 supportFragmentManager 기반)
    LaunchedEffect(Unit) {
        val activity = context as? AppCompatActivity ?: return@LaunchedEffect
        SelectAppDialogFragment.observeSelectedApp(
            activity.supportFragmentManager,
            lifecycleOwner,
        ) { viewModel.onAppsSelected(it) }
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is UsageTimerViewModel.EventShowTimerView -> {
                // 다른 앱 위에 표시 권한 없이 오버레이 창을 추가하면 크래시하므로 권한 설정으로 안내
                if (PermissionHelper.canDrawOverlays(context)) {
                    UsageTimerGlobal.showPreview(context)
                } else {
                    PermissionHelper.showDrawOverlaysSetting(context)
                }
            }
            is UsageTimerViewModel.EventShowToast -> {
                val message = context.getString(event.messageResId, *event.formatArgs.toTypedArray())
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            is UsageTimerViewModel.EventDetachTimerView -> {
                UsageTimerGlobal.detachAllViews()
            }
            is UsageTimerViewModel.EventOpenAccessibilitySettings -> {
                PermissionHelper.openAccessibilityServiceDetailSetting(context, UsageTimerAccessibility::class.java)
            }
            is UsageTimerViewModel.EventOpenSelectApp -> {
                val activity = context as? AppCompatActivity ?: return@CollectEvents
                SelectAppDialogFragment.newInstance(event.checkedPackageNames)
                    .show(activity.supportFragmentManager, SelectAppDialogFragment.TAG)
            }
            else -> Unit
        }
    }

    UsageTimerContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onPauseDurationInputChanged = viewModel::onPauseDurationInputChanged,
        onClickShow = viewModel::onClickShow,
        onClickSave = viewModel::onClickSave,
        onClickPause = viewModel::onClickPause,
        onClickCancelPause = viewModel::onClickCancelPause,
        onClickAccessibility = viewModel::onClickAccessibility,
        onClickSelectApp = viewModel::onClickSelectApp,
        onFinishTimer = viewModel::onFinishTimer,
    )
}

/** UsageTimer 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun UsageTimerContent(
    uiState: UsageTimerUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onPauseDurationInputChanged: (String) -> Unit,
    onClickShow: () -> Unit,
    onClickSave: () -> Unit,
    onClickPause: () -> Unit,
    onClickCancelPause: () -> Unit,
    onClickAccessibility: () -> Unit,
    onClickSelectApp: () -> Unit,
    onFinishTimer: () -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_usage_timer),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag("usage_timer_title"),
    ) {
        // 타이머 표시 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = AppColors.Divider,
        )
        Text(
            text = stringResource(R.string.usage_timer_section_overlay),
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )
        ActionButton(
            text = stringResource(R.string.usage_timer_show),
            onClick = onClickShow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("show_button"),
            letterSpacing = 0.sp,
        )

        // 일시정지 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = AppColors.Divider,
        )
        Text(
            text = stringResource(R.string.usage_timer_section_pause),
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = uiState.pauseDurationInput,
                onValueChange = onPauseDurationInputChanged,
                label = {
                    Text(
                        text = stringResource(R.string.usage_timer_pause_duration_label),
                        style = AppTypography.Caption,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.ContainerBorder,
                    focusedBorderColor = AppColors.Arrow,
                    focusedTextColor = AppColors.ItemName,
                    unfocusedTextColor = AppColors.ItemName,
                    cursorColor = AppColors.Title,
                    focusedLabelColor = AppColors.Caption,
                    unfocusedLabelColor = AppColors.Caption,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_duration_input"),
            )
            ActionButton(
                text = stringResource(R.string.usage_timer_save),
                onClick = onClickSave,
                modifier = Modifier
                    .height(56.dp)
                    .testTag("save_button"),
                letterSpacing = 0.sp,
            )
        }

        if (uiState.pauseRemainTimeMillis > 0L) {
            PauseRemainCountDown(
                pauseRemainTimeMillis = uiState.pauseRemainTimeMillis,
                onFinishTimer = onFinishTimer,
            )
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = stringResource(R.string.usage_timer_pause),
                onClick = onClickPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_button"),
                letterSpacing = 0.sp,
            )
            ActionButton(
                text = stringResource(R.string.usage_timer_cancel_pause),
                onClick = onClickCancelPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cancel_button"),
                letterSpacing = 0.sp,
            )
        }

        // 설정 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = AppColors.Divider,
        )
        Text(
            text = stringResource(R.string.usage_timer_section_settings),
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )
        ActionButton(
            text = stringResource(R.string.usage_timer_accessibility),
            onClick = onClickAccessibility,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("accessibility_button"),
            letterSpacing = 0.sp,
        )
        ActionButton(
            text = stringResource(R.string.select_app),
            onClick = onClickSelectApp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("select_app_button"),
            letterSpacing = 0.sp,
        )

        // 선택한 앱 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = AppColors.Divider,
        )
        Text(
            text = stringResource(R.string.usage_timer_section_selected_apps),
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )

        if (uiState.selectedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.usage_timer_selected_apps_empty),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("selected_apps_empty"),
                style = AppTypography.Body.copy(color = AppColors.Caption),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("selected_apps_list"),
            ) {
                items(uiState.selectedApps, key = { it.packageName }) { item ->
                    SelectedAppItemContent(item)
                }
            }
        }
    }
}

/** 일시정지 잔여 시간 카운트다운(1초 틱, 종료 시 onFinishTimer 호출) */
@Composable
private fun PauseRemainCountDown(
    pauseRemainTimeMillis: Long,
    onFinishTimer: () -> Unit,
) {
    var remainTimeMillis by remember(pauseRemainTimeMillis) {
        mutableLongStateOf(pauseRemainTimeMillis)
    }

    LaunchedEffect(pauseRemainTimeMillis) {
        val endElapsedRealtime = SystemClock.elapsedRealtime() + pauseRemainTimeMillis
        while (true) {
            val remain = endElapsedRealtime - SystemClock.elapsedRealtime()
            if (remain <= 0L) {
                onFinishTimer()
                break
            }
            remainTimeMillis = remain
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .testTag("pause_info"),
    ) {
        Text(
            text = stringResource(R.string.usage_timer_pause_remain),
            style = AppTypography.Caption,
        )
        Text(
            text = DataUtil.milliSecondsToTimeString("mm:ss", remainTimeMillis),
            color = AppColors.Title,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** 선택된 앱 한 건(아이콘+라벨) 표시 */
@Composable
private fun SelectedAppItemContent(item: SelectedAppUiItem) {
    val iconBitmap = remember(item.packageName) { item.icon.toBitmap().asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = iconBitmap,
            contentDescription = item.label,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = item.label,
            color = AppColors.Icon,
        )
    }
}
