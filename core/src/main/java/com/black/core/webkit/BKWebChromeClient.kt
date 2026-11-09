package com.black.core.webkit

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import com.black.core.R
import com.black.core.dialog.BKAlertDialog
import com.black.core.dialog.DialogManager.Companion.showByDialogManager
import com.black.core.util.Extensions.activity
import com.black.core.util.Log
import com.black.core.util.Util.startDeepLink

/**
 * BaseWebChromeClient
 * WebChromeClient 필수 동작 구현, 다른 클래스 의존성은 최소화
 * Created by jinhyuk.lee on 2022/05/18
 **/
class BKWebChromeClient : WebChromeClient() {
    interface Callback: OnReceivedTitle, OnProgressChanged

    fun interface OnReceivedTitle {
        fun onReceivedTitle(view: WebView, title: String)
    }

    fun interface OnProgressChanged {
        fun onProgressChanged(view: WebView, newProgress: Int)
    }

    private val callbackList = ArrayList<Callback>()

    private var activity: ComponentActivity? = null

    private var webViewFileChooser: WebViewFileChooser? = null
    private var onShowFileChooserCallback: ValueCallback<Array<Uri>>? = null

    /*
    전체화면 처리를 위한 View와 Callback
     */
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    fun setFileChooser(fileChooser: WebViewFileChooser?) {
        webViewFileChooser = fileChooser
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        val activity = view?.context?.activity()
            ?: run {
                Log.w("activity is null")
                result?.confirm()
                return true
            }

        if (activity.isFinishing) {
            Log.w("activity is finishing")
            result?.confirm()
            return true
        }

        BKAlertDialog(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .showByDialogManager(activity, onDismiss = { result?.confirm() })
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        val activity = view?.context?.activity()
            ?: run {
                // 사용자에게 묻지 못했으므로 승인이 아닌 취소로 응답
                Log.w("activity is null")
                result?.cancel()
                return true
            }

        if (activity.isFinishing) {
            Log.w("activity is finishing")
            result?.cancel()
            return true
        }

        BKAlertDialog(activity)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                result?.confirm()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setOnCancelListener{ result?.cancel() }
            .showByDialogManager(activity)
        return true
    }

    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
        Log.w("Console Webview", cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId())
        return true
    }

    /** 파일 업로드 구현 */
    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
        Log.v("onShowFileChooser")
        val fileChooser = webViewFileChooser
            ?: run {
                // 콜백을 쥔 채 응답하지 않으면 이후 파일 선택이 열리지 않으므로 기본 처리(취소)에 위임
                Log.w("webViewFileChooser not set")
                return false
            }

        // 응답하지 않은 이전 요청이 남아 있으면 취소해야 새 요청이 동작
        onShowFileChooserCallback?.onReceiveValue(null)
        onShowFileChooserCallback = filePathCallback
        fileChooser.showFileChooser(fileChooserParams, this::onOpenDocuments)
        return true
    }

    /** 웹뷰에서 전체화면 시에 호출되는 메소드 */
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        Log.v("view : $view, callback : $callback")
        customView = view ?: run {
            Log.w("view is null")
            callback?.onCustomViewHidden()
            return
        }

        val activity = view.context?.activity()
            ?: run {
                Log.w("activity is null")
                callback?.onCustomViewHidden()
                return
            }

        // onHideCustomView 처리를 위해 activity 저장
        this.activity = activity

        customViewCallback = callback

        if (view.parent != null) {
            activity.windowManager.removeView(view)
        }
        activity.windowManager.addView(view, WindowManager.LayoutParams())
    }

    /** 웹뷰에서 전체화면 해제시에 호출되는 메소드 */
    override fun onHideCustomView() {
        val activity = activity
            ?: run {
                Log.w("activity is null")
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                return
            }

        if (customView?.parent != null) {
            activity.windowManager.removeView(customView)
        }

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        this.activity = null
    }

    /** 현재 전체화면인지 여부 */
    fun isShownCustomView() : Boolean = customView != null

    /**
     * target='_blank', window.open() 등의 새 창 동작 처리
     * 웹뷰 객체 생성 후 새 창에서 열릴 URL을 로드시키고, WebViewClient로 획득한 새 창 URL을 원본 웹뷰에 로드시킨다.
     * 생성한 웹뷰 객체는 addView되지 않았으므로 보이지 않음
     */
    override fun onCreateWindow(currentWebView: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        Log.v("onCreateWindow")

        val context = currentWebView?.context?.applicationContext
            ?: run {
                Log.w("context is null")
                return true
            }

        // 웹뷰 객체 생성
        val webView = WebView(context)

        // onPageStarted에서 감지된 url을 원본 웹뷰에 로드시키는 WebViewClient를 셋팅.
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                // 외부 브라우저에서 링크 실행
                run { startDeepLink(context, url ?: return@run) }
                view?.stopLoading()
                // 링크 전달용 임시 웹뷰라 역할이 끝나면 파기(콜백 안에서 바로 파기하지 않도록 다음 루프로 미룸)
                view?.post { view.destroy() }
            }
        }

        // 생성한 웹뷰에 새 창 url을 로드시킨다.
        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = webView
        resultMsg?.sendToTarget()
        return true
    }

    @CallSuper
    override fun onReceivedTitle(view: WebView?, title: String?) {
        Log.v("onReceivedTitle : $title")

        // 제목이 변경되면 페이지 이동으로 가정하여 쿠키 동기화 진행
        CookieUtil.sync()

        view ?: return
        callbackList.forEach {
            it.onReceivedTitle(view, title ?: "")
        }
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        view ?: return

        callbackList.forEach {
            it.onProgressChanged(view, newProgress)
        }
    }

    /** 파일 열고 난 후 처리(선택하지 않고 닫으면 취소를 뜻하는 null 전달) */
    private fun onOpenDocuments(uriList: List<Uri>) {
        onShowFileChooserCallback?.let {
            it.onReceiveValue(uriList.takeIf { list -> list.isNotEmpty() }?.toTypedArray())
            onShowFileChooserCallback = null
        }
    }

    fun addCallback(callback: Callback) {
        callbackList.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbackList.remove(callback)
    }
}