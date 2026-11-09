package com.black.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Text의 기본 스타일([BlackTheme]이 제공하는 Material3 기본 bodyLarge)
 *
 * Text에 style을 넘기면 LocalTextStyle을 대체하므로, 이 스타일 위에 덮어써야 줄 높이(24sp)·줄 높이 정렬이
 * style 없이 쓴 Text와 같게 유지
 */
private val BaseTextStyle = Typography().bodyLarge

/**
 * 화면 공용 텍스트 스타일
 *
 * 영문 대문자 캡션은 모노스페이스에 자간을 넓히고([MonoCaption]), 한글 캡션은 자간을 넓히면 글자 사이가 벌어져 보여
 * SansSerif에 자간을 좁힌 별도 스타일([Caption]) 사용. 화면에 한 번만 쓰는 크기는 가까운 스타일을 copy로 조정
 */
object AppTypography {
    /** 큰 화면 제목(메인 화면이 크기를 키워 사용) */
    val ScreenTitle = BaseTextStyle.copy(
        color = AppColors.Title,
        fontSize = 34.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
    )

    /** 도구 화면 상단 바 제목 */
    val TopBarTitle = BaseTextStyle.copy(
        color = AppColors.Title,
        fontSize = 22.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp,
    )

    /** 영문 대문자 모노스페이스 캡션(섹션 머리·보조 라벨) */
    val MonoCaption = BaseTextStyle.copy(
        color = AppColors.Caption,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.5.sp,
    )

    /** 제목 위 영문 대문자 모노스페이스 캡션(날짜·TOOL) */
    val MonoHeaderCaption = MonoCaption.copy(fontWeight = FontWeight.Medium)

    /** 한글 캡션(섹션 머리·보조 라벨) */
    val Caption = BaseTextStyle.copy(
        color = AppColors.Caption,
        fontSize = 12.sp,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = 0.5.sp,
    )

    /** 한글 본문(상태 행·알림 메시지) */
    val Body = BaseTextStyle.copy(
        color = AppColors.Icon,
        fontSize = 14.sp,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = 0.sp,
    )

    /** 액션 버튼 라벨(영문 대문자 기준 자간) */
    val ButtonLabel = BaseTextStyle.copy(
        color = AppColors.ItemName,
        fontSize = 13.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
    )
}
