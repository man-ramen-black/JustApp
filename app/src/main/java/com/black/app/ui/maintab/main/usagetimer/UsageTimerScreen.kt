package com.black.app.ui.maintab.main.usagetimer

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.ui.common.selectapp.SelectAppDialogFragment
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.app.ui.theme.BoldListTokens
import com.black.core.util.DataUtil
import com.black.core.viewmodel.CollectEvents
import kotlinx.coroutines.delay

/** UsageTimer 화면 */
@Composable
fun UsageTimerScreen(
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
                UsageTimerView(context).attachView()
            }
            is UsageTimerViewModel.EventShowToast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
            is UsageTimerViewModel.EventDetachTimerView -> {
                UsageTimerGlobal.detachView()
            }
            is UsageTimerViewModel.EventOpenAccessibilitySettings -> {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
    onPauseDurationInputChanged: (String) -> Unit,
    onClickShow: () -> Unit,
    onClickSave: () -> Unit,
    onClickPause: () -> Unit,
    onClickCancelPause: () -> Unit,
    onClickAccessibility: () -> Unit,
    onClickSelectApp: () -> Unit,
    onFinishTimer: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoldListTokens.ScreenBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // 헤더 캡션
        Text(
            text = "TOOL",
            modifier = Modifier.padding(top = 26.dp),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.5.sp,
        )
        // 헤더 타이틀
        Text(
            text = "App Usage Timer",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("usage_timer_title"),
            color = BoldListTokens.TitleColor,
            fontSize = 34.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        )

        // OVERLAY 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = BoldListTokens.DividerColor,
        )
        Text(
            text = "OVERLAY",
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.5.sp,
        )
        BoldActionButton(
            text = "SHOW",
            onClick = onClickShow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("show_button"),
        )

        // PAUSE 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = BoldListTokens.DividerColor,
        )
        Text(
            text = "PAUSE",
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.5.sp,
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
                        text = "PAUSE DURATION (MIN)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BoldListTokens.ContainerBorder,
                    focusedBorderColor = BoldListTokens.ArrowColor,
                    focusedTextColor = BoldListTokens.ItemNameColor,
                    unfocusedTextColor = BoldListTokens.ItemNameColor,
                    cursorColor = BoldListTokens.TitleColor,
                    focusedLabelColor = BoldListTokens.CaptionColor,
                    unfocusedLabelColor = BoldListTokens.CaptionColor,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_duration_input"),
            )
            BoldActionButton(
                text = "SAVE",
                onClick = onClickSave,
                modifier = Modifier
                    .height(56.dp)
                    .testTag("save_button"),
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
            BoldActionButton(
                text = "PAUSE",
                onClick = onClickPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_button"),
            )
            BoldActionButton(
                text = "CANCEL",
                onClick = onClickCancelPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cancel_button"),
            )
        }

        // SETTINGS 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = BoldListTokens.DividerColor,
        )
        Text(
            text = "SETTINGS",
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.5.sp,
        )
        BoldActionButton(
            text = "ACCESSIBILITY",
            onClick = onClickAccessibility,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("accessibility_button"),
        )
        BoldActionButton(
            text = "SELECT APP",
            onClick = onClickSelectApp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("select_app_button"),
        )

        // SELECTED APPS 섹션
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp),
            thickness = 1.dp,
            color = BoldListTokens.DividerColor,
        )
        Text(
            text = "SELECTED APPS",
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.5.sp,
        )

        if (uiState.selectedApps.isEmpty()) {
            Text(
                text = "No apps selected",
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("selected_apps_empty"),
                color = BoldListTokens.CaptionColor,
                fontFamily = FontFamily.Monospace,
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

/** Bold 디자인 언어 액션 버튼(컨테이너 톤 박스 + ExtraBold 라벨) */
@Composable
private fun BoldActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BoldListTokens.ContainerShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(BoldListTokens.ContainerBackground)
            .border(1.dp, BoldListTokens.ContainerBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = BoldListTokens.ItemNameColor,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
        )
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
            text = "PAUSE REMAIN",
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.5.sp,
        )
        Text(
            text = DataUtil.milliSecondsToTimeString("mm:ss", remainTimeMillis),
            color = BoldListTokens.TitleColor,
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
            color = BoldListTokens.IconColor,
        )
    }
}
