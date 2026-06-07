package com.black.app.ui.maintab.main.usagetimer

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.core.viewmodel.CollectEvents
import com.black.app.ui.common.selectapp.SelectAppDialogFragment
import com.black.app.ui.common.selectapp.SelectedAppUiItem
import com.black.app.ui.maintab.main.usagetimer.view.UsageTimerView
import com.black.core.util.DataUtil
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "UsageTimer",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("usage_timer_title"),
        )

        Button(
            onClick = onClickShow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("show_button"),
        ) {
            Text("Show")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.pauseDurationInput,
                onValueChange = onPauseDurationInputChanged,
                label = { Text("Pause duration(min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_duration_input"),
            )
            Button(
                onClick = onClickSave,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .testTag("save_button"),
            ) {
                Text("Save")
            }
        }

        if (uiState.pauseRemainTimeMillis > 0L) {
            PauseRemainCountDown(
                pauseRemainTimeMillis = uiState.pauseRemainTimeMillis,
                onFinishTimer = onFinishTimer,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onClickPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pause_button"),
            ) {
                Text("Pause")
            }
            Button(
                onClick = onClickCancelPause,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cancel_button"),
            ) {
                Text("Cancel")
            }
        }

        Button(
            onClick = onClickAccessibility,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("accessibility_button"),
        ) {
            Text("Accessibility")
        }

        Button(
            onClick = onClickSelectApp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("select_app_button"),
        ) {
            Text("Select app")
        }

        if (uiState.selectedApps.isEmpty()) {
            Text(
                text = "No apps selected",
                modifier = Modifier
                    .padding(top = 15.dp)
                    .testTag("selected_apps_empty"),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 15.dp)
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

    Row(
        modifier = Modifier
            .padding(vertical = 15.dp)
            .testTag("pause_info"),
    ) {
        Text("Pause remain : ")
        Text(DataUtil.milliSecondsToTimeString("mm:ss", remainTimeMillis))
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
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
