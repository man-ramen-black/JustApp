package com.black.core.ui.component

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography

/**
 * 화면 공용 입력 칸(`OutlinedTextField`, `OutlinedTextFieldDefaults.DecorationBox`)의 색과 입력 글자 스타일
 *
 * 입력 글자를 Material 기본(16sp, 자간 0.5sp)에 맡기지 않도록 [InputTextStyle]을 textStyle로 함께 넘김
 */
object AppTextFieldDefaults {
    /**
     * 입력 글자 스타일
     *
     * textStyle에 색이 있으면 [colors]의 글자 색보다 우선하므로 글자 색이 같은 [AppTypography.BrightBody] 사용
     */
    val InputTextStyle: TextStyle = AppTypography.BrightBody

    /** 입력 칸 테두리·글자·커서·라벨 색(라벨이 없는 칸은 라벨 색이 표시에 영향 없음) */
    @Composable
    fun colors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = AppColors.ContainerBorder,
        focusedBorderColor = AppColors.Arrow,
        focusedTextColor = AppColors.ItemName,
        unfocusedTextColor = AppColors.ItemName,
        cursorColor = AppColors.Title,
        focusedLabelColor = AppColors.Caption,
        unfocusedLabelColor = AppColors.Caption,
    )
}
