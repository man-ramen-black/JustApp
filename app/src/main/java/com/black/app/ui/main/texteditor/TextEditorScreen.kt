package com.black.app.ui.main.texteditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import com.black.app.R
import com.black.core.ui.component.ConfirmDialog
import com.black.core.ui.component.ToolTopBar
import com.black.core.ui.component.TopBarIconButton
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography
import com.black.core.viewmodel.CollectEvents

/**
 * [TextEditorScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object TextEditorTestTags {
    const val TITLE = "text_editor_title"
    const val FILE_NAME = "file_name"
    const val SAVE_BUTTON = "save_button"
    const val LOAD_BUTTON = "load_button"
    const val NEW_BUTTON = "new_button"
    const val EDITOR_INPUT = "editor_input"
    const val PERMISSION_REQUEST_DIALOG = "permission_request_dialog"
    const val SELECT_DOCUMENT_BUTTON = "select_document_button"
    const val RESET_CONFIRM_DIALOG = "reset_confirm_dialog"
}

/** TextEditor 화면 */
@Composable
fun TextEditorScreen(
    onNavigateUp: () -> Unit,
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

    // 화면 이탈 시 자동 저장(저장이 끝날 때는 화면이 사라졌을 수 있어 결과 토스트는 애플리케이션 Context로 표시)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        val applicationContext = context.applicationContext
        viewModel.onPause { messageResId ->
            Toast.makeText(applicationContext, messageResId, Toast.LENGTH_SHORT).show()
        }
    }

    TextEditorContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
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
    onNavigateUp: () -> Unit,
    onClickSave: () -> Unit,
    onClickLoad: () -> Unit,
    onClickNew: () -> Unit,
    onTextChanged: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.ScreenBackground)
            .statusBarsPadding(),
    ) {
        // 저장·불러오기·새 문서는 상단 바 오른쪽 액션
        ToolTopBar(
            title = stringResource(R.string.fragment_name_text_editor),
            onNavigateUp = onNavigateUp,
            titleModifier = Modifier.testTag(TextEditorTestTags.TITLE),
        ) {
            TopBarIconButton(
                painter = painterResource(R.drawable.ic_save),
                contentDescription = stringResource(R.string.text_editor_save),
                onClick = onClickSave,
                modifier = Modifier.testTag(TextEditorTestTags.SAVE_BUTTON),
            )
            TopBarIconButton(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = stringResource(R.string.text_editor_load),
                onClick = onClickLoad,
                modifier = Modifier.testTag(TextEditorTestTags.LOAD_BUTTON),
            )
            TopBarIconButton(
                painter = painterResource(R.drawable.ic_note_add),
                contentDescription = stringResource(R.string.text_editor_new),
                onClick = onClickNew,
                modifier = Modifier.testTag(TextEditorTestTags.NEW_BUTTON),
            )
        }
        // 연 문서 이름
        if (uiState.fileName.isNotEmpty()) {
            Text(
                text = uiState.fileName,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(TextEditorTestTags.FILE_NAME),
                style = AppTypography.MonoCaption.copy(letterSpacing = 1.5.sp),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            thickness = 1.dp,
            color = AppColors.Divider,
        )

        // 본문 에디터
        BasicTextField(
            value = uiState.text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                .testTag(TextEditorTestTags.EDITOR_INPUT),
            textStyle = AppTypography.Body.copy(
                color = AppColors.ItemName,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            cursorBrush = SolidColor(AppColors.Title),
        )
    }
}


/** 최근 문서 접근 권한 재요청 다이얼로그(확인 시 문서 다시 선택) */
@Composable
private fun PermissionRequestDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = stringResource(R.string.text_editor_permission_required),
        confirmText = stringResource(R.string.text_editor_select_document),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(TextEditorTestTags.PERMISSION_REQUEST_DIALOG),
        confirmButtonModifier = Modifier.testTag(TextEditorTestTags.SELECT_DOCUMENT_BUTTON),
    )
}

/** 새 문서 초기화 확인 다이얼로그 */
@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = stringResource(R.string.text_editor_new_file),
        confirmText = stringResource(R.string.ok),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(TextEditorTestTags.RESET_CONFIRM_DIALOG),
    )
}
