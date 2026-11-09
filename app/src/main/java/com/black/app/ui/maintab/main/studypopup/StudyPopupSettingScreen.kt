package com.black.app.ui.maintab.main.studypopup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.R
import com.black.core.ui.component.ActionButton
import com.black.core.ui.component.ConfirmDialog
import com.black.core.ui.component.ContainerIconButton
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.StatusRow
import com.black.core.ui.component.SwitchRow
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography
import com.black.core.util.PermissionHelper
import com.black.core.viewmodel.CollectEvents

/**
 * [StudyPopupSettingScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object StudyPopupSettingTestTags {
    const val TITLE = "study_popup_title"
    const val ENABLED_SWITCH = "study_popup_enabled_switch"
    const val PREVIEW_BUTTON = "preview_button"
    const val OVERLAY_PERMISSION_STATUS = "overlay_permission_status"
    const val SERVICE_STATUS = "service_status"
    const val SAVED_CONTENTS_STATUS = "saved_contents_status"
    const val FILE_NAME = "file_name"
    const val LOAD_BUTTON = "load_button"
    const val RELOAD_BUTTON = "reload_button"
    const val SAVE_BUTTON = "save_button"
    const val CONTENTS_EMPTY = "contents_empty"
    const val ADD_BUTTON = "add_button"
    const val EDIT_DIALOG = "edit_dialog"
    const val EDIT_INPUT = "edit_input"
    const val EDIT_CONFIRM_BUTTON = "edit_confirm_button"
    const val DELETE_DIALOG = "delete_dialog"
    const val DELETE_CONFIRM_BUTTON = "delete_confirm_button"
    const val OVERLAY_PERMISSION_DIALOG = "overlay_permission_dialog"
    const val OPEN_OVERLAY_SETTINGS_BUTTON = "open_overlay_settings_button"

    /** [index]번째 내용 행 */
    fun contentsItem(index: Int) = "contents_item_$index"

    /** [index]번째 내용 행의 삭제 버튼 */
    fun deleteButton(index: Int) = "delete_button_$index"
}

/**
 * 스터디 팝업 설정 화면
 *
 * @param onOpenAppInstallNotification 백그라운드 서비스가 중지돼 있을 때 서비스를 켜는 앱 설치 알림 화면으로 이동
 */
@Composable
fun StudyPopupSettingScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAppInstallNotification: () -> Unit = {},
    viewModel: StudyPopupSettingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 파일 선택창은 저장소 권한 없이 사용자가 고른 파일에만 접근
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { viewModel.loadFile(it) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { viewModel.saveFileTo(it) }

    // 설정 앱에서 권한을 허용하고 돌아온 경우의 상태 갱신
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStatus()
    }

    // 일회성 이벤트 처리
    viewModel.CollectEvents { event ->
        when (event) {
            is StudyPopupSettingViewModel.EventOpenDocument -> {
                openDocumentLauncher.launch(arrayOf("text/xml", "application/xml"))
            }
            is StudyPopupSettingViewModel.EventCreateDocument -> {
                createDocumentLauncher.launch(context.getString(R.string.study_popup_default_file_name))
            }
            is StudyPopupSettingViewModel.EventShowPopupPreview -> {
                StudyPopupGlobal.showPreview(context)
            }
            is StudyPopupSettingViewModel.EventOpenOverlaySettings -> {
                PermissionHelper.showDrawOverlaysSetting(context)
            }
            is StudyPopupSettingViewModel.EventShowToast -> {
                Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    StudyPopupSettingContent(
        uiState = uiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onEnabledChange = viewModel::setEnabled,
        onClickPreview = viewModel::showPopupPreview,
        onClickOverlayPermission = viewModel::showOverlayPermissionDialog,
        onClickServiceStatus = onOpenAppInstallNotification,
        onClickLoad = viewModel::openFile,
        onClickReload = viewModel::reloadFile,
        onClickSave = viewModel::saveFile,
        onClickContents = viewModel::showEditDialog,
        onClickDelete = viewModel::showDeleteDialog,
        onClickAdd = viewModel::showAddDialog,
    )

    uiState.editingIndex?.let { index ->
        ContentsEditDialog(
            initialText = uiState.contents.getOrElse(index) { "" },
            onConfirm = viewModel::applyEdit,
            onDismiss = viewModel::dismissEditDialog,
        )
    }

    if (uiState.deletingIndex != null) {
        DeleteConfirmDialog(
            onConfirm = viewModel::deleteContents,
            onDismiss = viewModel::dismissDeleteDialog,
        )
    }

    if (uiState.showOverlayPermissionDialog) {
        OverlayPermissionDialog(
            onConfirm = viewModel::openOverlaySettings,
            onDismiss = viewModel::dismissOverlayPermissionDialog,
        )
    }
}

/** 스터디 팝업 설정 화면 콘텐츠(상태 무관 렌더링 전용) */
@Composable
private fun StudyPopupSettingContent(
    uiState: StudyPopupSettingUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClickPreview: () -> Unit,
    onClickOverlayPermission: () -> Unit,
    onClickServiceStatus: () -> Unit,
    onClickLoad: () -> Unit,
    onClickReload: () -> Unit,
    onClickSave: () -> Unit,
    onClickContents: (index: Int) -> Unit,
    onClickDelete: (index: Int) -> Unit,
    onClickAdd: () -> Unit,
) {
    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_study_popup),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(StudyPopupSettingTestTags.TITLE),
    ) {
        // 팝업 설정 섹션
        SectionHeader(title = stringResource(R.string.study_popup_section_setting))
        SwitchRow(
            label = stringResource(R.string.study_popup_enabled_label),
            checked = uiState.showOnScreenOn,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag(StudyPopupSettingTestTags.ENABLED_SWITCH),
        )
        Text(
            text = stringResource(R.string.study_popup_enabled_description),
            modifier = Modifier.padding(top = 8.dp),
            style = AppTypography.Caption.copy(lineHeight = 18.sp),
        )
        ActionButton(
            text = stringResource(R.string.study_popup_preview),
            onClick = onClickPreview,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .testTag(StudyPopupSettingTestTags.PREVIEW_BUTTON),
            letterSpacing = 0.sp,
        )

        // 상태 섹션
        SectionHeader(title = stringResource(R.string.study_popup_section_status))
        StatusRow(
            label = stringResource(R.string.study_popup_status_overlay),
            value = if (uiState.overlayPermissionGranted) {
                stringResource(R.string.study_popup_status_allowed)
            } else {
                stringResource(R.string.study_popup_status_required)
            },
            onClick = if (uiState.overlayPermissionGranted) null else onClickOverlayPermission,
            modifier = Modifier.testTag(StudyPopupSettingTestTags.OVERLAY_PERMISSION_STATUS),
        )
        StatusRow(
            label = stringResource(R.string.study_popup_status_service),
            value = if (uiState.serviceRunning) {
                stringResource(R.string.study_popup_status_running)
            } else {
                stringResource(R.string.study_popup_status_stopped)
            },
            onClick = if (uiState.serviceRunning) null else onClickServiceStatus,
            modifier = Modifier.testTag(StudyPopupSettingTestTags.SERVICE_STATUS),
        )
        StatusRow(
            label = stringResource(R.string.study_popup_status_saved_contents),
            value = stringResource(R.string.study_popup_saved_contents_count, uiState.savedContentsCount),
            onClick = null,
            modifier = Modifier.testTag(StudyPopupSettingTestTags.SAVED_CONTENTS_STATUS),
        )

        // 내용 파일 섹션
        SectionHeader(title = stringResource(R.string.study_popup_section_file))
        Text(
            text = uiState.fileName.ifEmpty { stringResource(R.string.study_popup_file_none) },
            modifier = Modifier.testTag(StudyPopupSettingTestTags.FILE_NAME),
            style = AppTypography.Body.copy(color = AppColors.ItemName),
        )
        // 저장하지 않은 변경이 있을 때만 표시
        if (uiState.modified) {
            Text(
                text = stringResource(R.string.study_popup_file_modified),
                modifier = Modifier.padding(top = 4.dp),
                style = AppTypography.Caption.copy(lineHeight = 18.sp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = stringResource(R.string.study_popup_load),
                onClick = onClickLoad,
                modifier = Modifier
                    .weight(1f)
                    .testTag(StudyPopupSettingTestTags.LOAD_BUTTON),
                letterSpacing = 0.sp,
            )
            // 다시 불러올 파일이 있을 때만 표시
            if (uiState.fileName.isNotEmpty()) {
                ActionButton(
                    text = stringResource(R.string.study_popup_reload),
                    onClick = onClickReload,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(StudyPopupSettingTestTags.RELOAD_BUTTON),
                    letterSpacing = 0.sp,
                )
            }
            ActionButton(
                text = stringResource(R.string.study_popup_save),
                onClick = onClickSave,
                modifier = Modifier
                    .weight(1f)
                    .testTag(StudyPopupSettingTestTags.SAVE_BUTTON),
                letterSpacing = 0.sp,
            )
        }

        // 내용 섹션
        SectionHeader(
            title = stringResource(R.string.study_popup_section_contents, uiState.contents.size),
        )
        if (uiState.contents.isEmpty()) {
            Text(
                text = stringResource(R.string.study_popup_contents_empty),
                modifier = Modifier.testTag(StudyPopupSettingTestTags.CONTENTS_EMPTY),
                style = AppTypography.Body.copy(color = AppColors.Caption),
            )
        } else {
            uiState.contents.forEachIndexed { index, contents ->
                if (index > 0) {
                    HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
                }
                ContentsRow(
                    contents = contents,
                    onClick = { onClickContents(index) },
                    onClickDelete = { onClickDelete(index) },
                    deleteButtonTestTag = StudyPopupSettingTestTags.deleteButton(index),
                    modifier = Modifier.testTag(StudyPopupSettingTestTags.contentsItem(index)),
                )
            }
        }
        ActionButton(
            text = stringResource(R.string.study_popup_add),
            onClick = onClickAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp)
                .testTag(StudyPopupSettingTestTags.ADD_BUTTON),
            letterSpacing = 0.sp,
        )
    }
}

/**
 * 내용 한 행(내용 + 삭제 버튼)
 *
 * 팝업이 내용을 HTML로 그리므로 목록도 같은 모양으로 최대 3줄까지 표시하고, 행을 누르면 편집
 */
@Composable
private fun ContentsRow(
    contents: String,
    onClick: () -> Unit,
    onClickDelete: () -> Unit,
    deleteButtonTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (contents.isBlank()) {
                AnnotatedString(stringResource(R.string.study_popup_list_contents_empty))
            } else {
                AnnotatedString.fromHtml(contents.replace("\n", "<br>"))
            },
            // 행 위아래 여백 12dp 중 6dp는 삭제 버튼 터치 영역(48dp)과 겹치도록 내용에만 적용
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            style = AppTypography.Body.copy(color = AppColors.ItemName),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        // 삭제 아이콘 버튼(보이는 박스 36dp, 터치 영역 48dp)
        // 보이는 박스 오른쪽 끝을 콘텐츠 여백에 맞추도록 터치 영역 차이의 절반(6dp)만큼 오른쪽으로 이동
        ContainerIconButton(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.study_popup_delete),
            onClick = onClickDelete,
            modifier = Modifier
                .offset(x = 6.dp)
                .testTag(deleteButtonTestTag),
            boxSize = 36.dp,
            iconSize = 16.dp,
        )
    }
}

/** 내용 입력 다이얼로그(빈 내용은 확인 불가) */
@Composable
private fun ContentsEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(StudyPopupSettingTestTags.EDIT_DIALOG),
        shape = AppShapes.Container,
        containerColor = AppColors.ContainerBackground,
        title = {
            Text(
                text = stringResource(R.string.study_popup_edit_title),
                color = AppColors.Title,
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StudyPopupSettingTestTags.EDIT_INPUT),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.ContainerBorder,
                    focusedBorderColor = AppColors.Arrow,
                    focusedTextColor = AppColors.ItemName,
                    unfocusedTextColor = AppColors.ItemName,
                    cursorColor = AppColors.Title,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag(StudyPopupSettingTestTags.EDIT_CONFIRM_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.ok),
                    color = if (text.isNotBlank()) AppColors.Title else AppColors.Arrow,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = AppColors.Caption)
            }
        },
    )
}

/** 내용 삭제 확인 다이얼로그 */
@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = stringResource(R.string.study_popup_delete_confirm),
        confirmText = stringResource(R.string.study_popup_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(StudyPopupSettingTestTags.DELETE_DIALOG),
        confirmButtonModifier = Modifier.testTag(StudyPopupSettingTestTags.DELETE_CONFIRM_BUTTON),
    )
}

/**
 * 다른 앱 위에 표시 권한 안내 다이얼로그
 *
 * 이 권한은 실행 중 허용 창이 없어, 확인을 누르면 이 앱의 권한 스위치가 있는 설정 화면을 바로 염
 */
@Composable
private fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        message = stringResource(R.string.study_popup_overlay_permission_guide),
        confirmText = stringResource(R.string.study_popup_open_overlay_settings),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(StudyPopupSettingTestTags.OVERLAY_PERMISSION_DIALOG),
        confirmButtonModifier = Modifier.testTag(StudyPopupSettingTestTags.OPEN_OVERLAY_SETTINGS_BUTTON),
    )
}
