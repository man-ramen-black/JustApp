package com.black.app.ui.main.calculator

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.black.app.R
import com.black.app.model.CalculatorTab
import com.black.app.model.RatioTerm
import com.black.core.ui.component.AppTextFieldDefaults
import com.black.core.ui.component.SectionHeader
import com.black.core.ui.component.ToolScreenScaffold
import com.black.core.ui.component.TopBarIconButton
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography

/** 비율 칸 사이 구분 기호 양옆 간격 */
private val RATIO_ROW_SPACING = 2.dp

/** 비율 칸 안쪽 좌우 여백 */
private val RATIO_FIELD_HORIZONTAL_PADDING = 4.dp

/**
 * 비율 입력 칸과 계산값의 글자 크기
 *
 * 계산값에 `TextAutoSize.StepBased`를 쓰면 칸 폭이 충분해도 최소 크기 근처로 그려져 자동 축소를 쓰지 않음.
 */
private val RATIO_FIELD_FONT_SIZE = AppTypography.Body.fontSize

/** Calculator 화면 */
@Composable
fun CalculatorScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    calculatorViewModel: CalculatorViewModel = hiltViewModel(),
    dilutionViewModel: DilutionViewModel = hiltViewModel(),
    ratioViewModel: RatioViewModel = hiltViewModel(),
) {
    val calculatorUiState by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val dilutionUiState by dilutionViewModel.uiState.collectAsStateWithLifecycle()
    val ratioUiState by ratioViewModel.uiState.collectAsStateWithLifecycle()

    CalculatorContent(
        calculatorUiState = calculatorUiState,
        dilutionUiState = dilutionUiState,
        ratioUiState = ratioUiState,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        onSelectTab = calculatorViewModel::selectTab,
        onClickClear = {
            // 현재 탭의 입력값만 비움
            when (calculatorUiState.selectedTab) {
                CalculatorTab.DILUTION -> dilutionViewModel.clearInputs()
                CalculatorTab.RATIO -> ratioViewModel.clearInputs()
                null -> Unit
            }
        },
        onChangeStockConcentration = dilutionViewModel::updateStockConcentration,
        onChangeTargetConcentration = dilutionViewModel::updateTargetConcentration,
        onChangeStockAmount = dilutionViewModel::updateStockAmount,
        onChangeDiluentAmount = dilutionViewModel::updateDiluentAmount,
        onChangeRatioTerm = ratioViewModel::updateTerm,
    )
}

/**
 * Calculator 화면 콘텐츠
 *
 * 상태를 갖지 않고 받은 값만 그리는 렌더링 전용.
 */
@Composable
private fun CalculatorContent(
    calculatorUiState: CalculatorUiState,
    dilutionUiState: DilutionUiState,
    ratioUiState: RatioUiState,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onSelectTab: (CalculatorTab) -> Unit,
    onClickClear: () -> Unit,
    onChangeStockConcentration: (String) -> Unit,
    onChangeTargetConcentration: (String) -> Unit,
    onChangeStockAmount: (String) -> Unit,
    onChangeDiluentAmount: (String) -> Unit,
    onChangeRatioTerm: (RatioTerm, String) -> Unit,
) {
    val selectedTab = calculatorUiState.selectedTab

    ToolScreenScaffold(
        title = stringResource(R.string.fragment_name_calculator),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        titleModifier = Modifier.testTag(CalculatorTestTags.TITLE),
        actions = {
            TopBarIconButton(
                painter = painterResource(R.drawable.ic_delete_sweep),
                contentDescription = stringResource(R.string.calculator_clear_inputs),
                onClick = onClickClear,
                modifier = Modifier.testTag(CalculatorTestTags.CLEAR_BUTTON),
            )
        },
    ) {
        // 저장된 탭을 읽기 전에는 탭과 내용을 그리지 않음
        if (selectedTab == null) return@ToolScreenScaffold

        CalculatorTabRow(selectedTab = selectedTab, onSelectTab = onSelectTab)
        when (selectedTab) {
            CalculatorTab.DILUTION -> DilutionTabContent(
                uiState = dilutionUiState,
                onChangeStockConcentration = onChangeStockConcentration,
                onChangeTargetConcentration = onChangeTargetConcentration,
                onChangeStockAmount = onChangeStockAmount,
                onChangeDiluentAmount = onChangeDiluentAmount,
            )
            CalculatorTab.RATIO -> RatioTabContent(
                uiState = ratioUiState,
                onChangeTerm = onChangeRatioTerm,
            )
        }
    }
}

/** 희석·비율 탭 */
@Composable
private fun CalculatorTabRow(
    selectedTab: CalculatorTab,
    onSelectTab: (CalculatorTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = AppColors.ScreenBackground,
        contentColor = AppColors.Title,
        indicator = { tabPositions ->
            // 테마 primary가 배경과 비슷해 선택 표시선 색을 직접 지정
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                color = AppColors.Title,
            )
        },
        // 바로 아래 SectionHeader의 구분선과 겹치지 않도록 기본 구분선을 없앰
        divider = {},
    ) {
        CalculatorTab.entries.forEach { tab ->
            val (labelResId, testTag) = when (tab) {
                CalculatorTab.DILUTION -> R.string.calculator_tab_dilution to CalculatorTestTags.DILUTION_TAB
                CalculatorTab.RATIO -> R.string.calculator_tab_ratio to CalculatorTestTags.RATIO_TAB
            }
            Tab(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag(testTag),
                selectedContentColor = AppColors.Title,
                unselectedContentColor = AppColors.Caption,
            ) {
                Text(
                    text = stringResource(labelResId),
                    modifier = Modifier.padding(vertical = 14.dp),
                    style = AppTypography.Body,
                    color = if (tab == selectedTab) AppColors.Title else AppColors.Caption,
                )
            }
        }
    }
}

/**
 * 희석 탭
 *
 * 원액·목표 농도와 두 양 칸 입력, 비워 둔 양 칸의 구한 값과 총량을 보여 줌.
 */
@Composable
private fun DilutionTabContent(
    uiState: DilutionUiState,
    onChangeStockConcentration: (String) -> Unit,
    onChangeTargetConcentration: (String) -> Unit,
    onChangeStockAmount: (String) -> Unit,
    onChangeDiluentAmount: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val result = uiState.result

    SectionHeader(stringResource(R.string.calculator_section_concentration))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DilutionInputField(
            value = uiState.inputs.stockConcentration,
            onValueChange = onChangeStockConcentration,
            imeAction = ImeAction.Next,
            modifier = Modifier
                .weight(1f)
                .testTag(CalculatorTestTags.STOCK_CONCENTRATION_INPUT),
            label = stringResource(R.string.calculator_stock),
        )
        DilutionInputField(
            value = uiState.inputs.targetConcentration,
            onValueChange = onChangeTargetConcentration,
            imeAction = ImeAction.Next,
            modifier = Modifier
                .weight(1f)
                .testTag(CalculatorTestTags.TARGET_CONCENTRATION_INPUT),
            label = stringResource(R.string.calculator_target),
        )
    }

    SectionHeader(stringResource(R.string.calculator_section_amount))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DilutionInputField(
            value = uiState.inputs.stockAmount,
            onValueChange = onChangeStockAmount,
            imeAction = ImeAction.Next,
            modifier = Modifier
                .weight(1f)
                .testTag(CalculatorTestTags.STOCK_AMOUNT_INPUT),
            label = stringResource(R.string.calculator_stock),
        )
        DilutionInputField(
            value = uiState.inputs.diluentAmount,
            onValueChange = onChangeDiluentAmount,
            imeAction = ImeAction.Done,
            modifier = Modifier
                .weight(1f)
                .testTag(CalculatorTestTags.DILUENT_AMOUNT_INPUT),
            label = stringResource(R.string.calculator_diluent),
            onDone = { focusManager.clearFocus() },
        )
    }

    SectionHeader(stringResource(R.string.calculator_section_result))
    val success = result as? DilutionResult.Success
    // 구하지 못한 상태에서는 농도 0 용액 라벨
    val solvedLabelResId = if (success?.solvedAmount == DilutionAmount.STOCK) {
        R.string.calculator_solved_stock
    } else {
        R.string.calculator_solved_diluent
    }
    val solvedAmountText = success?.let { CalculatorMath.formatNumber(it.amount) }
    val totalAmountText = success?.let { CalculatorMath.formatNumber(it.totalAmount) }
    Text(
        text = stringResource(solvedLabelResId),
        modifier = Modifier.testTag(CalculatorTestTags.SOLVED_AMOUNT_LABEL),
        style = AppTypography.Caption,
    )
    Text(
        text = solvedAmountText ?: stringResource(R.string.calculator_result_empty),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CalculatorTestTags.SOLVED_AMOUNT_RESULT),
        style = AppTypography.ResultValue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = stringResource(R.string.calculator_total_amount),
        modifier = Modifier.padding(top = 16.dp),
        style = AppTypography.Caption,
    )
    Text(
        text = totalAmountText ?: stringResource(R.string.calculator_result_empty),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CalculatorTestTags.TOTAL_AMOUNT_RESULT),
        style = AppTypography.Body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    // 오류 문구
    val errorResId = when (result) {
        DilutionResult.TargetZero -> R.string.calculator_error_target_zero
        DilutionResult.TargetAboveStock -> R.string.calculator_error_target_above_stock
        DilutionResult.SameConcentration -> R.string.calculator_error_same_concentration
        else -> null
    }
    if (errorResId != null) {
        Text(
            text = stringResource(errorResId),
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(CalculatorTestTags.DILUTION_ERROR),
            style = AppTypography.Body,
        )
    }
}

/**
 * 비율 탭
 *
 * `a : b = x : y` 한 줄 입력과 계산값을 보여 줌.
 */
@Composable
private fun RatioTabContent(
    uiState: RatioUiState,
    onChangeTerm: (RatioTerm, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val result = uiState.result

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(RATIO_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatioTerm.entries.forEachIndexed { index, term ->
            // 칸 사이 구분 기호(a : b = x : y)
            if (index > 0) {
                val separator = if (term == RatioTerm.X) "=" else ":"
                Text(text = separator, style = AppTypography.Body)
            }
            val termLabel = when (term) {
                RatioTerm.A -> stringResource(R.string.calculator_ratio_term_a)
                RatioTerm.B -> stringResource(R.string.calculator_ratio_term_b)
                RatioTerm.X -> stringResource(R.string.calculator_ratio_term_x)
                RatioTerm.Y -> stringResource(R.string.calculator_ratio_term_y)
            }
            val termTestTag = when (term) {
                RatioTerm.A -> CalculatorTestTags.RATIO_A_INPUT
                RatioTerm.B -> CalculatorTestTags.RATIO_B_INPUT
                RatioTerm.X -> CalculatorTestTags.RATIO_X_INPUT
                RatioTerm.Y -> CalculatorTestTags.RATIO_Y_INPUT
            }
            val calculatedText = (result as? RatioResult.Success)
                ?.takeIf { it.term == term }
                ?.let { CalculatorMath.formatNumber(it.value) }
            val isLastTerm = term == RatioTerm.Y

            RatioInputField(
                value = uiState.inputs.valueOf(term),
                onValueChange = { onChangeTerm(term, it) },
                imeAction = if (isLastTerm) ImeAction.Done else ImeAction.Next,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = termLabel }
                    .testTag(termTestTag),
                calculatedText = calculatedText,
                onDone = { focusManager.clearFocus() },
            )
        }
    }

    // 오류 문구
    if (result == RatioResult.DivideByZero) {
        Text(
            text = stringResource(R.string.calculator_error_divide_by_zero),
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(CalculatorTestTags.RATIO_ERROR),
            style = AppTypography.Body,
        )
    }
}

/**
 * 희석 탭의 입력 칸
 *
 * 소수를 한 줄로 입력.
 *
 * @param label 칸 위에 뜨는 라벨
 * @param onDone [ImeAction.Done]을 눌렀을 때의 동작
 */
@Composable
private fun DilutionInputField(
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    label: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = AppTextFieldDefaults.InputTextStyle,
        label = { Text(text = label, style = AppTypography.Caption) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = AppTextFieldDefaults.colors(),
    )
}

/**
 * 비율 탭의 입력 칸
 *
 * 소수를 한 줄로 가운데 정렬해 입력.
 *
 * 한 줄에 네 칸이 들어가므로 안쪽 좌우 여백을 [RATIO_FIELD_HORIZONTAL_PADDING]로 줄인 [BasicTextField]로 그림.
 * [calculatedText]는 칸이 비어 있는 동안 입력값과 같은 크기·다른 색으로 한 줄에 보이고
 * 칸 폭을 넘치면 말줄임.
 *
 * @param calculatedText 빈 칸 자리에 계산된 값(입력이 생기면 사라짐)
 * @param onDone [ImeAction.Done]을 눌렀을 때의 동작
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatioInputField(
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    calculatedText: String?,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = AppTextFieldDefaults.colors()
    val inputTextStyle = AppTextFieldDefaults.InputTextStyle.copy(
        fontSize = RATIO_FIELD_FONT_SIZE,
        textAlign = TextAlign.Center,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight),
        textStyle = inputTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(AppColors.Title),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = calculatedText?.let {
                    {
                        BasicText(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CalculatorTestTags.RATIO_RESULT),
                            style = AppTypography.Body.copy(
                                color = AppColors.CalculatedValue,
                                fontSize = RATIO_FIELD_FONT_SIZE,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    start = RATIO_FIELD_HORIZONTAL_PADDING,
                    end = RATIO_FIELD_HORIZONTAL_PADDING,
                ),
            )
        },
    )
}

/** [CalculatorScreen] 요소의 testTag */
internal object CalculatorTestTags {
    const val TITLE = "calculator_title"
    const val DILUTION_TAB = "dilution_tab"
    const val RATIO_TAB = "ratio_tab"
    const val CLEAR_BUTTON = "clear_button"
    const val STOCK_CONCENTRATION_INPUT = "stock_concentration_input"
    const val TARGET_CONCENTRATION_INPUT = "target_concentration_input"
    const val STOCK_AMOUNT_INPUT = "stock_amount_input"
    const val DILUENT_AMOUNT_INPUT = "diluent_amount_input"
    const val SOLVED_AMOUNT_LABEL = "solved_amount_label"
    const val SOLVED_AMOUNT_RESULT = "solved_amount_result"
    const val TOTAL_AMOUNT_RESULT = "total_amount_result"
    const val DILUTION_ERROR = "dilution_error"
    const val RATIO_A_INPUT = "ratio_a_input"
    const val RATIO_B_INPUT = "ratio_b_input"
    const val RATIO_X_INPUT = "ratio_x_input"
    const val RATIO_Y_INPUT = "ratio_y_input"
    const val RATIO_RESULT = "ratio_result"
    const val RATIO_ERROR = "ratio_error"
}
