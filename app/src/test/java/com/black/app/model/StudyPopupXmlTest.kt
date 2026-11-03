package com.black.app.model

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [StudyPopupXml]의 내용 파일 읽기·쓰기 형식 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudyPopupXmlTest : BaseTest() {

    /**
     * 예전 버전이 루트 없이 저장한 파일도 모든 내용을 읽는지 검증
     *
     * Given: 선언 뒤 루트 없이 `<contents>` 두 개가 나열된 문자열
     * When: parse 호출
     * Then: 두 내용을 순서대로 반환
     */
    @Test
    fun test_01_parseLegacyFormatWithoutRoot() = runTest {
        /** Given **/
        val text = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><contents>a</contents><contents>b</contents>"

        /** When **/
        val contents = StudyPopupXml.parse(text)

        /** Then **/
        assertEquals(listOf("a", "b"), contents)
    }

    /**
     * 저장한 내용을 그대로 다시 읽는지 검증
     *
     * Given: 줄바꿈·HTML 태그·특수 문자가 든 내용
     * When: serialize 결과를 parse
     * Then: 원래 내용과 같음
     */
    @Test
    fun test_02_serializeAndParseRoundTrip() = runTest {
        /** Given **/
        val original = listOf("첫 줄\n둘째 줄", "<b>굵게</b>", "a < b & c > d")

        /** When **/
        val contents = StudyPopupXml.parse(StudyPopupXml.serialize(original))

        /** Then **/
        assertEquals(original, contents)
    }

    /**
     * 사람이 들여쓰기로 고친 파일에서 태그 사이 공백을 내용으로 읽지 않는지 검증
     *
     * Given: 루트와 `<contents>` 사이에 줄바꿈·들여쓰기가 있는 문자열
     * When: parse 호출
     * Then: `<contents>` 안의 내용만 반환
     */
    @Test
    fun test_03_parseIgnoresWhitespaceBetweenTags() = runTest {
        /** Given **/
        val text = "<studyPopup>\n    <contents>a</contents>\n    <contents>b</contents>\n</studyPopup>"

        /** When **/
        val contents = StudyPopupXml.parse(text)

        /** Then **/
        assertEquals(listOf("a", "b"), contents)
    }
}
