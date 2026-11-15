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

    /**
     * 입력 칸 아래 오류 문구 스타일
     *
     * supportingText의 Text에 넘김.
     * 스타일에 색이 있으면 [colors]의 오류 문구 색보다 우선하므로 색이 같은 [AppTypography.ErrorCaption] 사용
     */
    val ErrorMessageTextStyle: TextStyle = AppTypography.ErrorCaption

    /**
     * 입력 칸 테두리·글자·커서·라벨 색과 오류 상태 색
     *
     * 라벨이 없는 칸은 라벨 색이 표시에 영향 없음.
     * 오류 상태에서도 글자·라벨은 평소 색을 유지하고 테두리·커서·오류 문구만 [AppColors.Error]로 표시
     */
    @Composable
    fun colors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = AppColors.ContainerBorder,
        focusedBorderColor = AppColors.Arrow,
        errorBorderColor = AppColors.Error,
        focusedTextColor = AppColors.ItemName,
        unfocusedTextColor = AppColors.ItemName,
        errorTextColor = AppColors.ItemName,
        cursorColor = AppColors.Title,
        errorCursorColor = AppColors.Error,
        focusedLabelColor = AppColors.Caption,
        unfocusedLabelColor = AppColors.Caption,
        errorLabelColor = AppColors.Caption,
        errorSupportingTextColor = AppColors.Error,
    )
}
