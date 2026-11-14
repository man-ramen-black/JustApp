package com.black.app.usagelog.collect

import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [NotificationDuplicateFilter]의 같은 내용 재게시 판정과 보관 상한 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationDuplicateFilterTest : BaseTest() {

    /**
     * 같은 key와 내용의 두 번째 호출이 중복인지 검증
     *
     * Given: 빈 필터
     * When: 같은 key, 제목, 본문으로 두 번 호출
     * Then: false, true
     */
    @Test
    fun test_01_sameContentSameKeyIsDuplicate() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()

        /** When / Then **/
        assertFalse(filter.isDuplicate("a", "제목", "본문", null))
        assertTrue(filter.isDuplicate("a", "제목", "본문", null))
    }

    /**
     * 본문이 바뀌면 중복이 아닌지 검증
     *
     * Given: 같은 key로 한 번 호출
     * When: 본문을 바꿔 호출
     * Then: false
     */
    @Test
    fun test_02_changedTextNotDuplicate() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        filter.isDuplicate("a", "제목", "본문", null)

        /** When / Then **/
        assertFalse(filter.isDuplicate("a", "제목", "바뀐 본문", null))
    }

    /**
     * 다른 key는 같은 내용이어도 중복이 아닌지 검증
     *
     * Given: key a로 한 번 호출
     * When: key b로 같은 내용 호출
     * Then: false
     */
    @Test
    fun test_03_otherKeyNotDuplicate() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        filter.isDuplicate("a", "제목", "본문", null)

        /** When / Then **/
        assertFalse(filter.isDuplicate("b", "제목", "본문", null))
    }

    /**
     * 상한을 넘으면 가장 오래된 항목부터 잊는지 검증
     *
     * Given: maxEntries=2, key a, b, c 순으로 호출
     * When: a를 같은 내용으로 다시 호출
     * Then: false
     */
    @Test
    fun test_04_evictsOldestBeyondMax() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter(maxEntries = 2)
        filter.isDuplicate("a", "제목", "본문", null)
        filter.isDuplicate("b", "제목", "본문", null)
        filter.isDuplicate("c", "제목", "본문", null)

        /** When / Then **/
        assertFalse(filter.isDuplicate("a", "제목", "본문", null))
    }

    /**
     * 잊은 key는 같은 내용이 다시 와도 중복이 아닌지 검증
     *
     * Given: key a로 한 번 호출 후 forget("a")
     * When: 같은 내용으로 다시 호출
     * Then: false
     */
    @Test
    fun test_05_forgetAllowsSameContentAgain() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        filter.isDuplicate("a", "제목", "본문", null)
        filter.forget("a")

        /** When / Then **/
        assertFalse(filter.isDuplicate("a", "제목", "본문", null))
    }

    /**
     * 같은 문구로 새로 온 메시지는 중복이 아니고, 같은 메시지의 재게시는 중복인지 검증
     *
     * Given: 메시지 시각 1000으로 key a, 제목, 본문 호출
     * When: 메시지 시각 2000으로 같은 key, 제목, 본문 호출 뒤 같은 값으로 다시 호출
     * Then: false, true
     */
    @Test
    fun test_06_differentMessageTimeNotDuplicate() = runTest {
        /** Given **/
        val filter = NotificationDuplicateFilter()
        filter.isDuplicate("a", "제목", "ㅇㅇ", 1_000L)

        /** When **/
        val newMessage = filter.isDuplicate("a", "제목", "ㅇㅇ", 2_000L)
        val repost = filter.isDuplicate("a", "제목", "ㅇㅇ", 2_000L)

        /** Then **/
        assertFalse(newMessage)
        assertTrue(repost)
    }
}
