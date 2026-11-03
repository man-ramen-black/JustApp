package com.black.app.ui.maintab.main.studypopup

/** 스터디 팝업 설정 화면 상태 */
data class StudyPopupSettingUiState(
    /** 화면을 켤 때 팝업 표시 설정 */
    val enabled: Boolean = false,
    /** 다른 앱 위에 표시 권한 허용 여부 */
    val overlayPermissionGranted: Boolean = false,
    /** 화면 켜짐을 감지하는 ForegroundService 실행 여부 */
    val serviceRunning: Boolean = false,
    /** 팝업 DB에 저장돼 화면을 켤 때 보여 줄 내용 개수 */
    val savedContentsCount: Int = 0,
    /** 연 내용 파일 이름. 연 파일이 없으면 빈 문자열 */
    val fileName: String = "",
    /** 편집 중인 내용 목록 */
    val contents: List<String> = emptyList(),
    /** 파일을 불러오거나 저장한 뒤 내용을 바꿨는지 여부 */
    val modified: Boolean = false,
    /** 편집 다이얼로그 대상 위치. [contents] 크기와 같으면 새 내용 추가, null이면 다이얼로그 숨김 */
    val editingIndex: Int? = null,
    /** 삭제 확인 다이얼로그 대상 위치. null이면 다이얼로그 숨김 */
    val deletingIndex: Int? = null,
    /** 다른 앱 위에 표시 권한 안내 다이얼로그 표시 여부 */
    val showOverlayPermissionDialog: Boolean = false,
)
