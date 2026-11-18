package com.black.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.black.core.R
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography

/** [OverlayPositionSetting]의 testTag 상수 */
object OverlayPositionTestTags {
    const val HORIZONTAL_LEFT = "overlay_position_horizontal_left"
    const val HORIZONTAL_CENTER = "overlay_position_horizontal_center"
    const val HORIZONTAL_RIGHT = "overlay_position_horizontal_right"
    const val VERTICAL_TOP = "overlay_position_vertical_top"
    const val VERTICAL_CENTER = "overlay_position_vertical_center"
    const val VERTICAL_BOTTOM = "overlay_position_vertical_bottom"
    const val MARGIN_TOP = "overlay_position_margin_top"
    const val MARGIN_BOTTOM = "overlay_position_margin_bottom"
    const val MARGIN_LEFT = "overlay_position_margin_left"
    const val MARGIN_RIGHT = "overlay_position_margin_right"
}

/**
 * 오버레이 표시 위치 설정
 *
 * 가로·세로 기준 선택과 네 방향 여백 입력으로 구성.
 * 섹션 제목은 포함하지 않으므로 화면이 [SectionHeader]로 둠
 */
@Composable
fun OverlayPositionSetting(
    input: OverlayPositionInput,
    onInputChange: (OverlayPositionInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnchorRow(
            label = stringResource(R.string.overlay_position_horizontal),
            options = listOf(
                AnchorOption(HorizontalAnchor.LEFT, R.string.overlay_position_left, OverlayPositionTestTags.HORIZONTAL_LEFT),
                AnchorOption(HorizontalAnchor.CENTER, R.string.overlay_position_center, OverlayPositionTestTags.HORIZONTAL_CENTER),
                AnchorOption(HorizontalAnchor.RIGHT, R.string.overlay_position_right, OverlayPositionTestTags.HORIZONTAL_RIGHT),
            ),
            selected = input.horizontal,
            onSelect = { onInputChange(input.copy(horizontal = it)) },
        )
        AnchorRow(
            label = stringResource(R.string.overlay_position_vertical),
            options = listOf(
                AnchorOption(VerticalAnchor.TOP, R.string.overlay_position_top, OverlayPositionTestTags.VERTICAL_TOP),
                AnchorOption(VerticalAnchor.CENTER, R.string.overlay_position_center, OverlayPositionTestTags.VERTICAL_CENTER),
                AnchorOption(VerticalAnchor.BOTTOM, R.string.overlay_position_bottom, OverlayPositionTestTags.VERTICAL_BOTTOM),
            ),
            selected = input.vertical,
            onSelect = { onInputChange(input.copy(vertical = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarginField(
                value = input.marginTop,
                onValueChange = { onInputChange(input.copy(marginTop = it)) },
                labelResId = R.string.overlay_position_margin_top,
                testTag = OverlayPositionTestTags.MARGIN_TOP,
                modifier = Modifier.weight(1f),
            )
            MarginField(
                value = input.marginBottom,
                onValueChange = { onInputChange(input.copy(marginBottom = it)) },
                labelResId = R.string.overlay_position_margin_bottom,
                testTag = OverlayPositionTestTags.MARGIN_BOTTOM,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarginField(
                value = input.marginLeft,
                onValueChange = { onInputChange(input.copy(marginLeft = it)) },
                labelResId = R.string.overlay_position_margin_left,
                testTag = OverlayPositionTestTags.MARGIN_LEFT,
                modifier = Modifier.weight(1f),
            )
            MarginField(
                value = input.marginRight,
                onValueChange = { onInputChange(input.copy(marginRight = it)) },
                labelResId = R.string.overlay_position_margin_right,
                testTag = OverlayPositionTestTags.MARGIN_RIGHT,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 방향 선택 버튼 하나의 값·문자열·testTag */
private class AnchorOption<T>(val value: T, @StringRes val labelResId: Int, val testTag: String)

/** 라벨과 방향 선택 버튼 행 */
@Composable
private fun <T> AnchorRow(
    label: String,
    options: List<AnchorOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = AppTypography.Caption)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.testTag(option.testTag),
                    // 테마 primary가 검은 배경과 구분되지 않으므로 AppColors 토큰으로 지정
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Title,
                        activeContentColor = AppColors.ScreenBackground,
                        activeBorderColor = AppColors.Title,
                        inactiveContainerColor = AppColors.ContainerBackground,
                        inactiveContentColor = AppColors.Icon,
                        inactiveBorderColor = AppColors.ContainerBorder,
                    ),
                    icon = {},
                    // AppTypography 스타일에 색이 들어 있어 SegmentedButton의 content 색을 덮으므로 선택 여부에 맞춰 직접 지정
                    label = {
                        Text(
                            text = stringResource(option.labelResId),
                            style = AppTypography.ButtonLabel.copy(
                                color = if (option.value == selected) AppColors.ScreenBackground else AppColors.Icon,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** 숫자만 입력되는 여백 입력 칸 */
@Composable
private fun MarginField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelResId: Int,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(OverlayPositionInput.filterMargin(it, value)) },
        modifier = modifier.testTag(testTag),
        label = { Text(text = stringResource(labelResId), style = AppTypography.Caption) },
        textStyle = AppTextFieldDefaults.InputTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = AppTextFieldDefaults.colors(),
    )
}
