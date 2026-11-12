package com.black.app.ui.main.texteditor

import android.net.Uri

/** TextEditor 화면 상태 */
data class TextEditorUiState(
    /** 현재 파일명. 빈 문자열이면 파일명 영역 숨김 */
    val fileName: String = "",
    /** 편집 중 텍스트 */
    val text: String = "",
    /** 새 문서 초기화 확인 다이얼로그 표시 여부 */
    val showResetConfirm: Boolean = false,
    /** 접근 권한을 다시 받아야 하는 마지막 문서 uri. null이 아니면 권한 요청 다이얼로그 표시 */
    val permissionRequiredUri: Uri? = null,
)
