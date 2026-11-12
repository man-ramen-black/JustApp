package com.black.app.ui.main.calculator

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import com.black.app.model.CalculatorRepository
import com.black.app.model.CalculatorTab
import com.black.app.model.DilutionInputs
import com.black.app.model.RatioInputs
import com.black.app.testutil.BaseUiTest
import com.black.core.ui.component.ToolTopBarTestTags
import com.black.core.ui.theme.BlackTheme
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * [CalculatorScreen]이 계측 환경에서 정상 동작하는지 검증하는 Compose UI 테스트
 *
 * HiltTestActivity(@AndroidEntryPoint)에 setContent로 호스팅해 hiltViewModel() 주입 동작 확인.
 * 탭과 입력값은 실제 앱 저장소를 쓰므로 테스트 전 값을 보관했다가 테스트 후 되돌림
 */
@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CalculatorScreenTest : BaseUiTest() {

    private val repository = CalculatorRepository(context)

    /** 테스트 전 사용자가 보던 탭, setup에서 읽기 전에는 null */
    private var originalTab: CalculatorTab? = null

    /** 테스트 전 사용자가 입력해 둔 희석 입력값, setup에서 읽기 전에는 null */
    private var originalDilutionInputs: DilutionInputs? = null

    /** 테스트 전 사용자가 입력해 둔 비율 입력값, setup에서 읽기 전에는 null */
    private var originalRatioInputs: RatioInputs? = null

    /** 사용자 탭·입력값을 보관한 뒤 희석 탭·빈 입력값으로 초기화 */
    override fun setup() {
        runBlocking {
            originalTab = repository.getSelectedTab()
            originalDilutionInputs = repository.getDilutionInputs()
            originalRatioInputs = repository.getRatioInputs()
            repository.saveSelectedTab(CalculatorTab.DILUTION)
            repository.saveDilutionInputs(DilutionInputs())
            repository.saveRatioInputs(RatioInputs())
        }
    }

    /**
     * 사용자 탭·입력값 복원
     *
     * 실사용 기기의 값을 유지하며, setup이 읽기 전에 실패했으면 덮어쓰지 않음.
     */
    override fun teardown() {
        runBlocking {
            originalTab?.let { repository.saveSelectedTab(it) }
            originalDilutionInputs?.let { repository.saveDilutionInputs(it) }
            originalRatioInputs?.let { repository.saveRatioInputs(it) }
        }
    }

    /**
     * CalculatorScreen이 핵심 요소를 정상 노출하는지 검증
     *
     * Given:
     * - HiltTestActivity에 CalculatorScreen 호스팅(희석 탭, 빈 입력값)
     * When:
     * - 초기 렌더링 상태 유지(별도 조작 없음)
     * Then:
     * - 타이틀 "계산기" 표시
     * - 희석·비율 탭 표시
     * - 희석 입력 칸 네 개와 지우기 버튼 표시
     */
    @Test
    fun test_01_displaysCoreElements() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        // 초기 렌더링 상태 그대로 검증(별도 조작 없음)

        /** Then **/
        assertTagDisplayed(CalculatorTestTags.TITLE)
        assertTagText(CalculatorTestTags.TITLE, "계산기")
        assertTagDisplayed(CalculatorTestTags.DILUTION_TAB)
        assertTagDisplayed(CalculatorTestTags.RATIO_TAB)
        assertTagDisplayed(CalculatorTestTags.STOCK_CONCENTRATION_INPUT)
        assertTagDisplayed(CalculatorTestTags.TARGET_CONCENTRATION_INPUT)
        assertTagDisplayed(CalculatorTestTags.STOCK_AMOUNT_INPUT)
        assertTagDisplayed(CalculatorTestTags.DILUENT_AMOUNT_INPUT)
        assertTagDisplayed(CalculatorTestTags.CLEAR_BUTTON)
    }

    /**
     * 희석 탭이 원액 양으로 섞을 농도 0 용액 양과 총량을 보여 주는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 원액 농도 9.8, 목표 농도 3, 원액 양 30 입력
     * Then:
     * - 라벨 "섞을 농도 0 용액", 구한 양 "68", 총량 "98" 표시
     */
    @Test
    fun test_02_dilutionShowsResult() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        inputDilution(stockConcentration = "9.8", targetConcentration = "3", stockAmount = "30")

        /** Then **/
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("68") }
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_LABEL, "섞을 농도 0 용액")
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_RESULT, "68")
        assertTagText(CalculatorTestTags.TOTAL_AMOUNT_RESULT, "98")
    }

    /**
     * 목표 농도가 원액 농도보다 높으면 오류 문구를 보여 주는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 원액 농도 3, 목표 농도 6, 원액 양 30 입력
     * Then:
     * - 오류 문구 "목표 농도가 원액 농도보다 높습니다.\n농도 0 용액을 섞어서는 만들 수 없습니다." 표시
     * - 두 결과 "-" 표시
     */
    @Test
    fun test_03_dilutionShowsTargetAboveError() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        inputDilution(stockConcentration = "3", targetConcentration = "6", stockAmount = "30")

        /** Then **/
        waitUntilTagExists(CalculatorTestTags.DILUTION_ERROR)
        assertTagText(
            CalculatorTestTags.DILUTION_ERROR,
            "목표 농도가 원액 농도보다 높습니다.\n농도 0 용액을 섞어서는 만들 수 없습니다.",
        )
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_RESULT, "-")
        assertTagText(CalculatorTestTags.TOTAL_AMOUNT_RESULT, "-")
    }

    /**
     * 허용되지 않는 입력은 무시되는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 원액 양 칸에 쉼표가 든 "1,5" 입력
     * Then:
     * - 원액 양 칸은 빈 채로 유지
     */
    @Test
    fun test_04_disallowedInputIgnored() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        replaceTagText(CalculatorTestTags.STOCK_AMOUNT_INPUT, "1,5")

        /** Then **/
        composeRule.waitForIdle()
        assertTagMatches(CalculatorTestTags.STOCK_AMOUNT_INPUT, hasEmptyInput())
    }

    /**
     * 비율 탭이 비어 있는 항을 계산값으로 채우는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 비율 탭 클릭 후 A·B·X에 1·2·3 입력
     * Then:
     * - 빈 Y 칸에 계산값 "6" 표시
     */
    @Test
    fun test_05_ratioFillsBlankTerm() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        clickTag(CalculatorTestTags.RATIO_TAB)
        inputRatio(a = "1", b = "2", x = "3")

        /** Then **/
        // 계산값은 빈 칸의 placeholder Text라 병합되지 않은 트리에서 찾음
        waitUntilTagMatches(CalculatorTestTags.RATIO_RESULT, useUnmergedTree = true) { hasText("6") }
        assertTagText(CalculatorTestTags.RATIO_RESULT, "6", useUnmergedTree = true)
    }

    /**
     * 비율 탭이 0으로 나누게 되는 입력에 오류 문구를 보여 주는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 비율 탭 클릭 후 A·B·X에 0·2·3 입력
     * Then:
     * - 비율 오류 문구 표시
     */
    @Test
    fun test_06_ratioShowsDivideByZeroError() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        clickTag(CalculatorTestTags.RATIO_TAB)
        inputRatio(a = "0", b = "2", x = "3")

        /** Then **/
        waitUntilTagExists(CalculatorTestTags.RATIO_ERROR)
        assertTagDisplayed(CalculatorTestTags.RATIO_ERROR)
    }

    /**
     * 지우기 버튼이 현재 탭의 입력값을 비우는지 검증
     *
     * Given:
     * - 희석 탭에 세 칸 입력(결과 표시 중)
     * When:
     * - 지우기 버튼 클릭
     * Then:
     * - 네 칸 모두 빈 칸
     * - 두 결과 "-" 표시
     */
    @Test
    fun test_07_clearButtonEmptiesCurrentTab() {
        /** Given **/
        hostCalculatorScreen()
        inputDilution(stockConcentration = "9.8", targetConcentration = "3", stockAmount = "30")
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("68") }

        /** When **/
        clickTag(CalculatorTestTags.CLEAR_BUTTON)

        /** Then **/
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("-") }
        assertTagMatches(CalculatorTestTags.STOCK_CONCENTRATION_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.TARGET_CONCENTRATION_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.STOCK_AMOUNT_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.DILUENT_AMOUNT_INPUT, hasEmptyInput())
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_RESULT, "-")
        assertTagText(CalculatorTestTags.TOTAL_AMOUNT_RESULT, "-")
    }

    /**
     * 상단 뒤로가기 버튼이 onNavigateUp 콜백을 호출하는지 검증
     *
     * Given:
     * - 호출 횟수를 기록하는 콜백으로 CalculatorScreen 호스팅
     * When:
     * - 뒤로가기 버튼 클릭
     * Then:
     * - 콜백 1회 호출
     */
    @Test
    fun test_08_navigateUpCallsCallback() {
        /** Given **/
        var navigateUpCount = 0
        hostCalculatorScreen { navigateUpCount++ }

        /** When **/
        clickTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON)

        /** Then **/
        assertEquals(1, navigateUpCount)
    }

    /**
     * 지우기 버튼이 다른 탭의 입력값은 남기는지 검증
     *
     * Given:
     * - 비율 탭에 A·B·X 입력 후 희석 탭으로 돌아가 세 칸 입력
     * When:
     * - 지우기 버튼 클릭
     * Then:
     * - 희석 탭 네 칸 모두 빈 칸
     * - 비율 탭으로 가면 A·B·X 값 유지
     */
    @Test
    fun test_09_clearButtonKeepsOtherTab() {
        /** Given **/
        hostCalculatorScreen()
        clickTag(CalculatorTestTags.RATIO_TAB)
        inputRatio(a = "1", b = "2", x = "3")
        clickTag(CalculatorTestTags.DILUTION_TAB)
        inputDilution(stockConcentration = "9.8", targetConcentration = "3", stockAmount = "30")
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("68") }

        /** When **/
        clickTag(CalculatorTestTags.CLEAR_BUTTON)

        /** Then **/
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("-") }
        assertTagMatches(CalculatorTestTags.STOCK_CONCENTRATION_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.TARGET_CONCENTRATION_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.STOCK_AMOUNT_INPUT, hasEmptyInput())
        assertTagMatches(CalculatorTestTags.DILUENT_AMOUNT_INPUT, hasEmptyInput())
        clickTag(CalculatorTestTags.RATIO_TAB)
        waitUntilTagExists(CalculatorTestTags.RATIO_A_INPUT)
        assertTagMatches(CalculatorTestTags.RATIO_A_INPUT, hasEditableText("1"))
        assertTagMatches(CalculatorTestTags.RATIO_B_INPUT, hasEditableText("2"))
        assertTagMatches(CalculatorTestTags.RATIO_X_INPUT, hasEditableText("3"))
    }

    /**
     * 희석 탭이 농도 0 용액 양으로 넣을 원액 양과 총량을 보여 주는지 검증
     *
     * Given:
     * - 희석 탭 호스팅
     * When:
     * - 원액 농도 9.8, 목표 농도 3, 농도 0 용액 양 68 입력
     * Then:
     * - 라벨 "넣을 원액", 구한 양 "30", 총량 "98" 표시
     */
    @Test
    fun test_10_dilutionSolvesStockAmount() {
        /** Given **/
        hostCalculatorScreen()

        /** When **/
        inputDilution(stockConcentration = "9.8", targetConcentration = "3", diluentAmount = "68")

        /** Then **/
        waitUntilTagMatches(CalculatorTestTags.SOLVED_AMOUNT_RESULT) { hasText("30") }
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_LABEL, "넣을 원액")
        assertTagText(CalculatorTestTags.SOLVED_AMOUNT_RESULT, "30")
        assertTagText(CalculatorTestTags.TOTAL_AMOUNT_RESULT, "98")
    }

    /**
     * 희석 탭의 [stockConcentration]·[targetConcentration]과 [stockAmount]·[diluentAmount] 중 값이 있는 칸 입력
     *
     * 빈 문자열로 둔 양 칸은 건드리지 않아 비워 둠.
     */
    private fun inputDilution(
        stockConcentration: String,
        targetConcentration: String,
        stockAmount: String = "",
        diluentAmount: String = "",
    ) {
        replaceTagText(CalculatorTestTags.STOCK_CONCENTRATION_INPUT, stockConcentration)
        replaceTagText(CalculatorTestTags.TARGET_CONCENTRATION_INPUT, targetConcentration)
        if (stockAmount.isNotEmpty()) replaceTagText(CalculatorTestTags.STOCK_AMOUNT_INPUT, stockAmount)
        if (diluentAmount.isNotEmpty()) replaceTagText(CalculatorTestTags.DILUENT_AMOUNT_INPUT, diluentAmount)
    }

    /**
     * 비율 탭 A·B·X 칸에 [a]·[b]·[x] 입력
     *
     * Y 칸은 비움.
     */
    private fun inputRatio(a: String, b: String, x: String) {
        replaceTagText(CalculatorTestTags.RATIO_A_INPUT, a)
        replaceTagText(CalculatorTestTags.RATIO_B_INPUT, b)
        replaceTagText(CalculatorTestTags.RATIO_X_INPUT, x)
    }

    /**
     * 입력 칸의 편집 텍스트가 빈 문자열인지 확인하는 매처
     *
     * 라벨·placeholder 텍스트와 무관하게 입력값만 비교.
     */
    private fun hasEmptyInput(): SemanticsMatcher = hasEditableText("")

    /** 입력 칸의 편집 텍스트가 [text]인지 확인하는 매처 */
    private fun hasEditableText(text: String): SemanticsMatcher =
        SemanticsMatcher("입력값이 \"$text\"") {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == text
        }

    /**
     * CalculatorScreen을 HiltTestActivity에 setContent로 호스팅하고 저장된 탭이 읽혀 탭이 그려질 때까지 대기
     *
     * [onNavigateUp]은 뒤로가기 버튼 클릭 시 호출
     */
    private fun hostCalculatorScreen(onNavigateUp: () -> Unit = {}) {
        onHostActivity { activity ->
            activity.setContent {
                BlackTheme {
                    CalculatorScreen(onNavigateUp = onNavigateUp)
                }
            }
        }
        // 저장된 탭을 읽기 전에는 탭을 그리지 않음
        waitUntilTagExists(CalculatorTestTags.DILUTION_TAB)
    }
}
