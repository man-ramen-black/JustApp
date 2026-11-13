package com.black.core.webkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.MimeTypeMap
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import com.black.core.R
import com.black.core.dialog.BKAlertDialogBuilder
import com.black.core.dialog.DialogManager.Companion.showByDialogManager
import com.black.core.util.Extensions.activity
import com.black.core.util.Log
import com.black.core.util.Util.startActivitySafely
import com.black.core.util.Util.startDeepLink
import java.net.URISyntaxException


/**
 * WebViewClient 필수 동작 구현, 다른 클래스 의존성은 최소화
 * created by jinhyuk.lee on 2020.01.17
 */
open class BKWebViewClient : WebViewClient() {

    companion object {
        private const val SCHEME_INTENT = "intent"
        private const val SCHEME_MARKET = "market"

        /** 웹 페이지가 지정할 수 없게 지우는 URI 권한 부여 플래그 */
        private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        /**
         * intent:// URI를 웹 페이지가 실행해도 되는 인텐트로 변환(파싱 실패면 null)
         *
         * 웹 페이지가 앱의 비공개 Activity를 열거나 앱의 URI 권한을 넘기지 못하도록
         * BROWSABLE 카테고리 강제, component·selector와 URI 권한 부여 플래그 제거
         */
        internal fun parseIntentDeepLink(uri: Uri): Intent? {
            val intent = try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                Log.w(e)
                return null
            }

            return intent.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                flags = flags and URI_GRANT_FLAGS.inv()
            }
        }
    }
    interface Callback: OnPageStarted, OnPageLoading, OnPageFinished, OnVisitedHistoryUpdated

    fun interface OnPageStarted {
        fun onPageStarted(view: WebView, url: String, favicon: Bitmap?)
    }

    fun interface OnPageLoading {
        fun onPageLoading(view: WebView, uri: Uri) : Boolean
    }

    fun interface OnPageFinished {
        fun onPageFinished(view: WebView, url: String, isError: Boolean)
    }

    fun interface OnVisitedHistoryUpdated {
        fun onVisitedHistoryUpdated(view: WebView, url: String, isReload: Boolean)
    }

    // 에러 여부
    var isError = false
        protected set

    // SSL 에러 일괄 처리용 리스트
    private val sslErrorHandlers = ArrayList<SslErrorHandler>()

    private val callbackList = ArrayList<Callback>()

    @CallSuper
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.d(url)
        isError = false
        view ?: return
        callbackList.forEach {
            it.onPageStarted(view, url ?: "", favicon)
        }
    }

    final override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        Log.d("url: $url, isForMainFrame: ${request?.isForMainFrame}, isRedirect: ${request?.isRedirect}")
        return onPageLoadingInternal(view, url)
    }

    private fun onPageLoadingInternal(view: WebView?, url: String?) : Boolean {
        if (view == null) {
            Log.w("view is null")
            return false
        }

        if (url.isNullOrEmpty()) {
            Log.w("url is null or empty.")
            return false
        }

        val uri = Uri.parse(url)
        if (uri == null) {
            Log.w("Uri.parse(url) is null.")
            return false
        }

        Log.d("scheme: ${uri.scheme}, host: ${uri.host}, path: ${uri.path}")
        return onPageLoading(view.context, view, uri)
    }

    /**
     * Url별 처리 (shouldOverrideUrlLoading 대체)
     * Default로 DeeplinkManager를 통해 처리
     */
    protected open fun onPageLoading(context : Context, view: WebView, uri: Uri) : Boolean {
        callbackList.forEach {
            if (it.onPageLoading(view, uri)) {
                return true
            }
        }

        when {
            // intent:// : intnet scheme 동작
            uri.scheme.equals(SCHEME_INTENT, true) -> {
                startIntentDeepLink(context, uri, view)
            }

            // market:// : 스토어 이동
            uri.scheme.equals(SCHEME_MARKET, true) -> {
                startDeepLink(context, uri)
            }

            // http : 일반 링크 이동
            uri.scheme?.startsWith("http") == true -> {
                return false
            }

            else -> {
                // 알 수 없는 링크 : 딥링크 실행
                startDeepLink(context, uri)
            }
        }
        // http 외에 Url은 페이지 이동되지 않도록 true 리턴
        return true
    }

    /** intent:// 딥링크 처리 */
    private fun startIntentDeepLink(context: Context, uri: Uri, webView: WebView?){
        val intent = parseIntentDeepLink(uri) ?: return

        if (startActivitySafely(context, intent)) {
            return
        }

        // 대상 앱이 없거나 대상 Activity가 권한을 요구해(SecurityException) 실패하면 대체 URL, 스토어 순으로 이동
        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrEmpty()) {
            webView?.loadUrl(fallbackUrl)
            return
        }

        val packageName = intent.getPackage()
        if (!packageName.isNullOrEmpty()) {
            startDeepLink(context, "market://details?id=$packageName")
        }
    }

    final override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(url)

        // 페이지 로드 완료 후 쿠키 동기화 진행
        CookieUtil.sync()

        onPageFinished(view ?: return, url ?: "", isError)
    }

    /** 페이지 로드 완료 */
    protected open fun onPageFinished(view: WebView, url: String, isError: Boolean) {
        callbackList.forEach { it.onPageFinished(view, url, isError) }
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.d(error)
        val activity = view?.context?.activity()
            ?: run {
                Log.w("context is not activity")
                super.onReceivedSslError(view, handler, error)
                return
            }

        /*
        ssl error 다수 발생 시 에러 팝업이 중복으로 노출되지 않도록 구현
         */
        // 에러 핸들러 리스트가 비어있지 않으면 팝업이 이미 노출 중이므로 handler만 추가
        if (sslErrorHandlers.isNotEmpty()) {
            sslErrorHandlers.add(handler ?: return)
            return
        }

        // 핸들러 추가 후 팝업 노출
        run { sslErrorHandlers.add(handler ?: return) }

        BKAlertDialogBuilder(activity)
            .setMessage(R.string.ssl_error_confirm_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                sslErrorHandlers.removeAll {
                    it.proceed()
                    true
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setOnCancelListener {
                // onPageFinished에서 에러뷰가 노출되도록 isError = true로 설정
                isError = true
                sslErrorHandlers.removeAll {
                    it.cancel()
                    true
                }
            }
            .showByDialogManager(activity)
    }

    /**
     * 히스토리 업데이트 시 발생하는 이벤트
     * onPageFinished가 호출되지 않는 페이지의 로드 완료를 감지할 수 있음
     */
    @CallSuper
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        Log.d("url : $url, isReload : $isReload")
        super.doUpdateVisitedHistory(view, url, isReload)
        callbackList.forEach() { it.onVisitedHistoryUpdated(view ?: return, url ?: "", isReload) }
    }

    final override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        onReceiveError(view, error?.errorCode?:0, error?.description?.toString(), request?.url?.toString())
        super.onReceivedError(view, request, error)
    }

    @CallSuper
    protected open fun onReceiveError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?){
        Log.w("$failingUrl : [$errorCode] $description")

        val extension = MimeTypeMap.getFileExtensionFromUrl(Uri.parse(failingUrl ?: "").toString())
        if (listOf("mp4").contains(extension)) {
            // 특정 확장자에서 발생한 에러 무시
            Log.i("Ignore error : $failingUrl")
            return
        }

        isError = true
    }

    fun addCallback(callback: Callback) {
        callbackList.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbackList.remove(callback)
    }
}