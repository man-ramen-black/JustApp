package com.black.app.ui.maintab.main.texteditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
    // 권한 재요청은 선택 결과를 일반 열기와 같은 경로로 로드(로드 시 영구 권한 획득)
    val reopenDocumentLauncher = rememberLauncherForActivityResult(
        OpenTextDocumentAt()
    ) { viewModel.onDocumentOpened(it) }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is TextEditorViewModel.EventOpenDocument -> {
                openDocumentLauncher.launch(arrayOf("text/plain"))
            }
            is TextEditorViewModel.EventCreateDocument -> {
                createDocumentLauncher.launch(".txt")
            }
            is TextEditorViewModel.EventReopenDocument -> {
                reopenDocumentLauncher.launch(event.uri)
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

    TextEditorContent(
        uiState = uiState,
        modifier = modifier,
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

    if (uiState.permissionRequiredUri != null) {
        PermissionRequestDialog(
            onConfirm = viewModel::onConfirmPermissionRequest,
            onDismiss = viewModel::onDismissPermissionRequest,
        )
    }
}

/**
 * text/plain 문서 열기 선택창을 입력 uri 문서가 있는 위치에서 여는 계약
 *
 * EXTRA_INITIAL_URI는 Android 8.0부터 지원하며, 이전 버전은 기본 위치에서 열림
 */
private class OpenTextDocumentAt : ActivityResultContract<Uri, Uri?>() {
    private val openDocument = ActivityResultContracts.OpenDocument()

    override fun createIntent(context: Context, input: Uri): Intent {
        return openDocument.createIntent(context, arrayOf("text/plain")).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return openDocument.parseResult(resultCode, intent)
    }
}

/** TextEditor 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun TextEditorContent(
    uiState: TextEditorUiState,
    modifier: Modifier = Modifier,
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
        // 헤더(캡션+타이틀) + 우측 액션 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOOL",
                    color = BoldListTokens.CaptionColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.5.sp,
                )
                Text(
                    text = "Memo",
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
            ToolbarIconButton(
                iconResId = R.drawable.ic_save,
                onClick = onClickSave,
                testTag = "save_button",
                modifier = Modifier.padding(start = 8.dp),
            )
            ToolbarIconButton(
                iconResId = R.drawable.ic_folder_open,
                onClick = onClickLoad,
                testTag = "load_button",
                modifier = Modifier.padding(start = 8.dp),
            )
            ToolbarIconButton(
                iconResId = R.drawable.ic_note_add,
                onClick = onClickNew,
                testTag = "new_button",
                modifier = Modifier.padding(start = 8.dp),
            )
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

/** 툴바 아이콘 버튼(40dp, ContainerShape, ContainerBackground 배경) */
@Composable
private fun ToolbarIconButton(
    iconResId: Int,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = BoldListTokens.ContainerShape
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

/** 최근 문서 접근 권한 재요청 다이얼로그(확인 시 문서 다시 선택) */
@Composable
private fun PermissionRequestDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("permission_request_dialog"),
        shape = BoldListTokens.ContainerShape,
        containerColor = BoldListTokens.ContainerBackground,
        text = {
            Text(
                text = stringResource(R.string.text_editor_permission_required),
                color = BoldListTokens.ItemNameColor,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("select_document_button"),
            ) {
                Text(stringResource(R.string.text_editor_select_document), color = BoldListTokens.TitleColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = BoldListTokens.CaptionColor)
            }
        },
    )
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
        shape = BoldListTokens.ContainerShape,
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
