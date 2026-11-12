package com.black.core.ui.theme

import androidx.compose.ui.graphics.Color

/** 기존 colors.xml 다크 색상을 매핑한 Material3 고정 다크 테마 */
object BlackThemeColors {
    val WindowBackground = Color(0xFF000000)
    val Primary = Color(0xFF121212)
    val Accent = Color(0xFFDDDDDD)
    val TextSub = Color(0xFFBBBBBB)
    val TextHint = Color(0xFF888888)
}

/** 메인·도구 화면 공용 색 */
object AppColors {
    val ScreenBackground = Color(0xFF000000)
    val Title = Color(0xFFFFFFFF)
    /** 보조 문구(섹션 제목·설명), 검은 배경 대비 5.2:1로 본문 권장 대비 4.5:1 이상 */
    val Caption = Color(0xFF7A7F88)
    val ItemName = Color(0xFFF5F5F5)
    val Icon = Color(0xFFD6D6D8)
    val ContainerBackground = Color(0xFF131316)
    val ContainerBorder = Color(0xFF222226)
    val Arrow = Color(0xFF43464D)
    /** 꺼진 스위치 손잡이·테두리, 트랙(ContainerBackground) 대비 4.6:1로 UI 요소 권장 대비 3:1 이상 */
    val UncheckedControl = Color(0xFF7A7F88)
    val Divider = Color(0xFF1A1B1E)
    /** 입력 칸 안에 보여 주는 계산값 색, 입력값(ItemName)과 구분되는 푸른 계열이며 검은 배경 대비 9:1 이상 */
    val CalculatedValue = Color(0xFF7FB2FF)
    /** 밝은 상단바 미리보기 배경 */
    val LightPreviewBackground = Color(0xFFF2F2F4)
    /** 밝은 상단바 미리보기 아이콘 색 */
    val LightPreviewContent = Color(0xFF1A1A1C)
}
