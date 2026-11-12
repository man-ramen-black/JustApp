package com.black.core.webkit

import android.content.Intent
import android.net.Uri
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** [BKWebViewClient.parseIntentDeepLink]의 intent:// URI 정리 동작 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BKWebViewClientTest : BaseTest() {

    /**
     * component·selector가 지정된 intent URI에서 둘을 지우고 BROWSABLE을 붙이는지 검증
     *
     * Given: component와 selector, extra가 들어 있는 intent URI
     * When: parseIntentDeepLink 호출
     * Then: component·selector가 null, CATEGORY_BROWSABLE 포함, extra는 유지
     */
    @Test
    fun test_01_removesComponentAndSelector() = runTest {
        /** Given **/
        val target = Intent(Intent.ACTION_VIEW)
            .setClassName("com.black.app", "com.black.app.ui.MainActivity")
            .putExtra("key", "value")
            .apply { selector = Intent(Intent.ACTION_MAIN).setClassName("com.black.app", "com.black.app.Hidden") }
        val uri = Uri.parse(target.toUri(Intent.URI_INTENT_SCHEME))

        /** When **/
        val intent = BKWebViewClient.parseIntentDeepLink(uri)

        /** Then **/
        assertNotNull(intent)
        assertNull(intent!!.component)
        assertNull(intent.selector)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("value", intent.getStringExtra("key"))
    }

    /**
     * 웹 페이지가 지정한 URI 권한 부여 플래그를 지우고 나머지 플래그는 유지하는지 검증
     *
     * Given: 읽기·쓰기 URI 권한 부여 플래그와 NEW_TASK 플래그, 대상 패키지가 들어 있는 intent URI
     * When: parseIntentDeepLink 호출
     * Then: URI 권한 부여 플래그는 없고 NEW_TASK와 대상 패키지는 남음
     */
    @Test
    fun test_02_removesUriGrantFlags() = runTest {
        /** Given **/
        val target = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.black.app.provider/file"))
            .setPackage("com.other.app")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        val uri = Uri.parse(target.toUri(Intent.URI_INTENT_SCHEME))

        /** When **/
        val intent = BKWebViewClient.parseIntentDeepLink(uri)

        /** Then **/
        val flags = intent!!.flags
        assertEquals(0, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals("com.other.app", intent.`package`)
    }

    /**
     * 파싱할 수 없는 intent URI는 null을 돌려주는지 검증
     *
     * Given: 끝 표시(end)가 없는 잘못된 intent URI
     * When: parseIntentDeepLink 호출
     * Then: null 반환
     */
    @Test
    fun test_03_invalidUriReturnsNull() = runTest {
        /** Given **/
        val uri = Uri.parse("intent://host#Intent;scheme=https;S.key=value")

        /** When **/
        val intent = BKWebViewClient.parseIntentDeepLink(uri)

        /** Then **/
        assertNull(intent)
    }
}
