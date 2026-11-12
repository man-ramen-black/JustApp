package com.black.core.webkit

import android.content.Intent
import android.net.Uri
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.black.test.BaseTest
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.RuntimeEnvironment

/** [BKWebChromeClient]가 Activity·파일 선택기가 없을 때 응답하는 방식 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BKWebChromeClientTest : BaseTest() {

    private val webView = WebView(RuntimeEnvironment.getApplication())

    /**
     * Activity가 없으면 JS confirm을 승인하지 않고 취소하는지 검증
     *
     * Given: applicationContext로 만든 WebView
     * When: onJsConfirm 호출
     * Then: true 반환, JsResult.cancel 호출, confirm 미호출
     */
    @Test
    fun test_01_confirmWithoutActivityIsCanceled() = runTest {
        /** Given **/
        val client = BKWebChromeClient()
        val result: JsResult = mockk(relaxed = true)

        /** When **/
        val handled = client.onJsConfirm(webView, "https://example.com", "message", result)

        /** Then **/
        assertTrue(handled)
        verify(exactly = 1) { result.cancel() }
        verify(exactly = 0) { result.confirm() }
    }

    /**
     * 파일 선택기가 없으면 콜백을 쥐지 않고 기본 처리에 맡기는지 검증
     *
     * Given: 파일 선택기를 설정하지 않은 BKWebChromeClient
     * When: onShowFileChooser 호출
     * Then: false 반환, 콜백 미호출
     */
    @Test
    fun test_02_fileChooserWithoutChooserReturnsFalse() = runTest {
        /** Given **/
        val client = BKWebChromeClient()
        val callback: ValueCallback<Array<Uri>> = mockk(relaxed = true)
        val params = TestFileChooserParams()

        /** When **/
        val handled = client.onShowFileChooser(webView, callback, params)

        /** Then **/
        assertFalse(handled)
        verify(exactly = 0) { callback.onReceiveValue(any()) }
    }

    /** Robolectric에서 FileChooserParams 목업이 실패해 쓰는 고정 값 구현 */
    private class TestFileChooserParams : WebChromeClient.FileChooserParams() {
        override fun getMode(): Int = MODE_OPEN
        override fun getAcceptTypes(): Array<String> = emptyArray()
        override fun isCaptureEnabled(): Boolean = false
        override fun getTitle(): CharSequence? = null
        override fun getFilenameHint(): String? = null
        override fun createIntent(): Intent = Intent()
    }
}
