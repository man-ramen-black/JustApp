package com.black.core.webkit

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.black.test.BaseTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [BKWebViewClient]의 intent:// 딥링크 처리 검증
 *
 * [BKWebViewClient.parseIntentDeepLink]의 intent:// URI 정리 동작과
 * 시작 실패 시 대체 URL, 스토어 순으로 넘어가는 동작 검증
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BKWebViewClientTest : BaseTest() {

    private val application: Application = RuntimeEnvironment.getApplication()

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

    /**
     * intent:// 딥링크 시작에 성공하면 대체 URL을 열지 않는지 검증
     *
     * Given: 애플리케이션 컨텍스트의 WebView, 대상 패키지와 대체 URL이 들어 있는 intent URI
     * When: shouldOverrideUrlLoading 호출
     * Then: true 반환, 대상 패키지의 인텐트 시작, 대체 URL 미로드
     */
    @Test
    fun test_04_startedIntentSkipsFallback() = runTest {
        /** Given **/
        val webView = WebView(application)
        val uri = intentDeepLink(fallbackUrl = FALLBACK_URL)

        /** When **/
        val handled = BKWebViewClient().shouldOverrideUrlLoading(webView, request(uri))

        /** Then **/
        assertTrue(handled)
        assertEquals(TARGET_PACKAGE, shadowOf(application).nextStartedActivity.`package`)
        assertNull(shadowOf(webView).lastLoadedUrl)
    }

    /**
     * 대상 앱이 없어 ActivityNotFoundException이 나면 대체 URL을 여는지 검증
     *
     * Given: 등록된 Activity만 시작하도록 검사를 켠 애플리케이션 컨텍스트의 WebView, 대체 URL이 들어 있는 intent URI
     * When: shouldOverrideUrlLoading 호출
     * Then: true 반환, WebView가 대체 URL 로드
     */
    @Test
    fun test_05_activityNotFoundLoadsFallbackUrl() = runTest {
        /** Given **/
        shadowOf(application).checkActivities(true)
        val webView = WebView(application)
        val uri = intentDeepLink(fallbackUrl = FALLBACK_URL)

        /** When **/
        val handled = BKWebViewClient().shouldOverrideUrlLoading(webView, request(uri))

        /** Then **/
        assertTrue(handled)
        assertEquals(FALLBACK_URL, shadowOf(webView).lastLoadedUrl)
    }

    /**
     * 대상 Activity가 권한을 요구해 SecurityException이 나도 대체 URL을 여는지 검증
     *
     * Given: startActivity가 SecurityException을 던지는 컨텍스트의 WebView, 대체 URL이 들어 있는 intent URI
     * When: shouldOverrideUrlLoading 호출
     * Then: true 반환, 시작 시도 1회, WebView가 대체 URL 로드
     */
    @Test
    fun test_06_securityExceptionLoadsFallbackUrl() = runTest {
        /** Given **/
        val context = SecurityExceptionContext(application)
        val webView = WebView(context)
        val uri = intentDeepLink(fallbackUrl = FALLBACK_URL)

        /** When **/
        val handled = BKWebViewClient().shouldOverrideUrlLoading(webView, request(uri))

        /** Then **/
        assertTrue(handled)
        assertEquals(1, context.startedIntents.size)
        assertEquals(FALLBACK_URL, shadowOf(webView).lastLoadedUrl)
    }

    /**
     * 시작에 실패하고 대체 URL이 없으면 대상 패키지의 스토어 페이지를 여는지 검증
     *
     * Given: startActivity가 SecurityException을 던지는 컨텍스트의 WebView, 대체 URL 없이 대상 패키지만 있는 intent URI
     * When: shouldOverrideUrlLoading 호출
     * Then: 예외 없이 true 반환, 두 번째 시작 인텐트가 market://details?id=<대상 패키지>의 ACTION_VIEW, 대체 URL 미로드
     */
    @Test
    fun test_07_failureWithoutFallbackOpensStore() = runTest {
        /** Given **/
        val context = SecurityExceptionContext(application)
        val webView = WebView(context)
        val uri = intentDeepLink(fallbackUrl = null)

        /** When **/
        val handled = BKWebViewClient().shouldOverrideUrlLoading(webView, request(uri))

        /** Then **/
        assertTrue(handled)
        assertEquals(2, context.startedIntents.size)
        val storeIntent = context.startedIntents[1]
        assertEquals(Intent.ACTION_VIEW, storeIntent.action)
        assertEquals("market://details?id=$TARGET_PACKAGE", storeIntent.dataString)
        assertNull(shadowOf(webView).lastLoadedUrl)
    }

    /** 대상 패키지와 선택적 대체 URL을 담은 intent:// URI */
    private fun intentDeepLink(fallbackUrl: String?): Uri {
        val target = Intent(Intent.ACTION_VIEW, Uri.parse("blackapptest://open"))
            .setPackage(TARGET_PACKAGE)
            .apply { fallbackUrl?.let { putExtra("browser_fallback_url", it) } }
        return Uri.parse(target.toUri(Intent.URI_INTENT_SCHEME))
    }

    /** 메인 프레임의 일반 이동 요청 */
    private fun request(uri: Uri): WebResourceRequest = mockk {
        every { url } returns uri
        every { isForMainFrame } returns true
        every { isRedirect } returns false
    }

    /** startActivity 요청을 기록하고 SecurityException을 던지는 컨텍스트 */
    private class SecurityExceptionContext(base: Context) : ContextWrapper(base) {
        val startedIntents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            startedIntents.add(intent)
            throw SecurityException("Permission Denial")
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.black.core.test.target"
        const val FALLBACK_URL = "https://example.com/fallback"
    }
}
