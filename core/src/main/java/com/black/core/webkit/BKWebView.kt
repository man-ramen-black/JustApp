/*
 * Copyright (c) 2020-present Netmarble Corp.
 *
 * This file contains Original Code and/or Modifications of Original Code as defined
 * in and that are subject to the Netmarble SDK Source License(the 'License').
 * You may not use this file except in compliance with the License.
 * Please obtain a copy of the License at https://developer.nmn.io/docs/policy/sdk-terms
 * and read it before using this file.
 *
 * The Original Code and all software distributed under the License are distributed
 * on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * AND NETMARBLE HEREBY DISCLAIMS ALL SUCH WARRANTIES, INCLUDING WITHOUT LIMITATION,
 * ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * QUIET ENJOYMENT OR NON-INFRINGEMENT. Please see the License for the specific language
 * governing rights and limitations under the License.
 */

package com.black.core.webkit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.doOnDetach
import com.black.core.BuildConfig
import com.black.core.util.Log

open class BKWebView : WebView {
    private var bkWebChromeClient: BKWebChromeClient? = null
    private var bkWebViewClient: BKWebViewClient? = null

    /** 현재 페이지 에러 여부 */
    val isError : Boolean
        get() = bkWebViewClient?.isError ?: run {
            Log.w("'${BKWebViewClient::class.java.simpleName}' is required to run 'isError()'")
            false
        }

    constructor(context: Context) : super(context){
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs){
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        initialize()
    }

    /** 웹뷰 기본 설정 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initialize() {
        // 쿠키 셋팅
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@BKWebView, true)
        }

        settings.apply {
            // 자바스크립트 활성화
            javaScriptEnabled = true

            /*
            웹에서 target=_blank, window.show() 등 새 창 이벤트가 발생할 때
            WebChromeClient.onCreateWindow 메소드가 호출되도록 설정
             */
            setSupportMultipleWindows(true)

            javaScriptCanOpenWindowsAutomatically = true

            // 기기 설정의 폰트사이즈(small, normal, large) 무시
            textZoom = 100

            // https 웹페이지에서 http 이미지 로드 허용
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 위치 정보 기능 활성화
            setGeolocationEnabled(true)

            // 웹에서 설정한 ViewPort에 맞게 노출되도록 설정
            useWideViewPort = true

            // 화면보다 큰 웹페이지를 기기 화면에 맞게 축소하여 표시
            loadWithOverviewMode = true

            // 파일 접근 허용
            allowFileAccess = true
            // 컨텐츠 접근 허용
            allowContentAccess = true
            // 캐쉬 모드 설정 : 디폴트
            cacheMode = WebSettings.LOAD_DEFAULT
            // HTML5에서 데이터 베이스 기능 사용
            databaseEnabled = true
            // HTML5에서 DOM Storage 기능 사용
            domStorageEnabled = true
            // 비밀번호 저장 사용안함
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                saveFormData = false
            }
            // 줌아웃 버튼 미표시
            displayZoomControls = false
        }

        // 스크롤바 스타일
        scrollBarStyle = SCROLLBARS_OUTSIDE_OVERLAY

        // 스크롤 시에 페이딩으로 스크롤바 표시
        isScrollbarFadingEnabled = true

        if (BuildConfig.DEBUG) {
            setWebContentsDebuggingEnabled(true);
        }

        // 기본 WebViewClient, WebChromeClient을 설정
        this.webViewClient = BKWebViewClient()
        this.webChromeClient = BKWebChromeClient()

        // 페이지 로드 시 흰색 화면 또는 검은색 화면이 노출되지 않도록 배경 투명 설정
        // (최초 페이지 로드 시 뒤에 있는 뷰가 노출되도록 설정)
        setBackgroundColor(Color.TRANSPARENT)

        doOnDetach {
            Log.d("WebView detached")

            // 메모리 최적화
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            clearCache(false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P){
                destroyDrawingCache()
            }
            destroy()
        }
    }

    override fun setWebChromeClient(client: WebChromeClient?) {
        super.setWebChromeClient(client)
        if(client is BKWebChromeClient){
            bkWebChromeClient = client
        }
    }

    override fun setWebViewClient(client: WebViewClient) {
        super.setWebViewClient(client)
        if(client is BKWebViewClient){
            bkWebViewClient = client
        }
    }

    fun isShowingCustomView() : Boolean{
        return bkWebChromeClient?.isShownCustomView() ?: run {
            Log.w("'${BKWebChromeClient::class.java.simpleName}' is required to 'isShownCustomView()'")
            false
        }
    }

    fun hideCustomView(){
        bkWebChromeClient?.onHideCustomView() ?: run {
            Log.w("'${BKWebChromeClient::class.java.simpleName}' is required to 'hideCustomView()'")
        }
    }

    @SuppressLint("JavascriptInterface")
    fun addJavascriptInterface(obj: Any) {
        addJavascriptInterface(obj, "native")
    }

    fun onBackPressed() : Boolean {
        return if (isShowingCustomView()) {
            hideCustomView()
            true
        } else if (canGoBack()) {
            goBack()
            true
        } else {
            false
        }
    }

    fun addWebViewClientCallback(callback: BKWebViewClient.Callback) {
        bkWebViewClient?.addListener(callback)
            ?: run { Log.w("nmWebViewClient is null") }
    }

    fun removeWebViewClientCallback(callback: BKWebViewClient.Callback) {
        bkWebViewClient?.removeListener(callback)
            ?: run { Log.v("nmWebViewClient is null") }
    }

    fun addWebChromeClientCallback(callback: BKWebChromeClient.Callback) {
        bkWebChromeClient?.addListener(callback)
            ?: run { Log.w("nmWebChromeClient is null") }
    }

    fun removeWebChromeClientCallback(callback: BKWebChromeClient.Callback) {
        bkWebChromeClient?.removeListener(callback)
            ?: run { Log.v("nmWebChromeClient is null") }
    }

    /**
     * NMWebChromeClient 파일 업로드가 동작을 위한 FileChooser 설정
     */
    fun setFileChooser(fileChooser: WebViewFileChooser?) {
        bkWebChromeClient?.setFileChooser(fileChooser)
            ?: run { Log.w("nmWebChromeClient is null") }
    }

    /**
     * 중복 스크롤 예외 처리 구현(ex. 스크롤 뷰 내에 웹뷰)
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // NestedScrollView 내에 웹뷰가 있는 경우 스크롤이 될 수 있도록
        // 상하 스크롤이 가능한 경우 터치 이벤트를 상위 뷰로 전달하지 않고, 웹뷰에서만 처리
        if(computeVerticalScrollRange() > measuredHeight) {
            requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(event)
    }
}
