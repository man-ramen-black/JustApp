package com.black.app.ui.maintab.main.texteditor

import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import com.black.app.R
import com.black.app.ui.theme.BoldListTokens
import com.black.core.viewmodel.CollectEvents

/** TextEditor 화면 */
@Composable
fun TextEditorScreen(
    modifier: Modifier = Modifier,
    viewModel: TextEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { viewModel.onDocumentOpened(it) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { viewModel.onDocumentCreated(it) }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is TextEditorViewModel.EventOpenDocument -> {
                openDocumentLauncher.launch(arrayOf("text/plain"))
            }
            is TextEditorViewModel.EventCreateDocument -> {
                createDocumentLauncher.launch(".txt")
            }
            is TextEditorViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    // 화면 이탈 시 자동 저장
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onPause()
    }

    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    TextEditorContent(
        uiState = uiState,
        modifier = modifier,
        onClickBack = { backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed() },
        onClickSave = viewModel::onClickSave,
        onClickLoad = viewModel::onClickLoad,
        onClickNew = viewModel::onClickNew,
        onTextChanged = viewModel::onTextChanged,
    )

    if (uiState.showResetConfirm) {
        ResetConfirmDialog(
            onConfirm = viewModel::onConfirmReset,
            onDismiss = viewModel::onDismissReset,
        )
    }
}

/** TextEditor 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun TextEditorContent(
    uiState: TextEditorUiState,
    modifier: Modifier = Modifier,
    onClickBack: () -> Unit,
    onClickSave: () -> Unit,
    onClickLoad: () -> Unit,
    onClickNew: () -> Unit,
    onTextChanged: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoldListTokens.ScreenBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // 상단 액션 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIconButton(
                iconResId = R.drawable.ic_back,
                onClick = onClickBack,
                testTag = "back_button",
            )
            Spacer(Modifier.weight(1f))
            ToolbarIconButton(
                iconResId = R.drawable.ic_save,
                onClick = onClickSave,
                testTag = "save_button",
            )
            ToolbarIconButton(
                iconResId = R.drawable.ic_load,
                onClick = onClickLoad,
                testTag = "load_button",
                modifier = Modifier.padding(start = 8.dp),
            )
            ToolbarIconButton(
                iconResId = R.drawable.ic_add,
                onClick = onClickNew,
                testTag = "new_button",
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 헤더
        Column(modifier = Modifier.padding(top = 18.dp)) {
            Text(
                text = "TOOL",
                color = BoldListTokens.CaptionColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp,
            )
            Text(
                text = "Text Editor",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("text_editor_title"),
                color = BoldListTokens.TitleColor,
                fontSize = 34.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            if (uiState.fileName.isNotEmpty()) {
                Text(
                    text = uiState.fileName,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("file_name"),
                    color = BoldListTokens.CaptionColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            thickness = 1.dp,
            color = BoldListTokens.DividerColor,
        )

        // 본문 에디터
        BasicTextField(
            value = uiState.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
                .testTag("editor_input"),
            textStyle = TextStyle(
                color = BoldListTokens.ItemNameColor,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            cursorBrush = SolidColor(BoldListTokens.TitleColor),
        )
    }
}

/** 툴바 아이콘 버튼(40dp, RoundedCornerShape 12dp, ContainerBackground 배경) */
@Composable
private fun ToolbarIconButton(
    iconResId: Int,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(BoldListTokens.ContainerBackground, shape)
            .border(1.dp, BoldListTokens.ContainerBorder, shape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = BoldListTokens.IconColor,
        )
    }
}

/** 새 문서 초기화 확인 다이얼로그 */
@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reset_confirm_dialog"),
        containerColor = BoldListTokens.ContainerBackground,
        text = {
            Text(
                text = stringResource(R.string.text_editor_new_file),
                color = BoldListTokens.ItemNameColor,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok), color = BoldListTokens.TitleColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = BoldListTokens.CaptionColor)
            }
        },
    )
}
