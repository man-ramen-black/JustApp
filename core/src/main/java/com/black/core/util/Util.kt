package com.black.core.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileUriExposedException

object Util {
    /** 딥링크 실행 */
    fun startDeepLink(context: Context?, url: String?)
            = startDeepLink(context, Uri.parse(url ?: ""))

    fun startDeepLink(context: Context?, uri: Uri) {
        if(context == null){
            Log.w("context is null")
            return
        }

        val scheme = uri.scheme
        if (scheme == null) {
            Log.e("invalid uri : $uri")
            return
        }

        Log.v("deeplink : $uri")
        startActivitySafely(context, Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * [intent]로 Activity를 시작하고 시작 요청 성공 여부 반환
     *
     * [context]가 Activity가 아니면 새 태스크 없이는 시작할 수 없어 [Intent.FLAG_ACTIVITY_NEW_TASK]를 더함(호출부가 넣은 플래그는 유지).
     *
     * 시작 실패는 예외 종류와 상관없이 모두 false로 돌려줌. 문서화된 예외는 시작 대상이 없을 때의
     * [ActivityNotFoundException]뿐이지만, 대상이 권한을 요구하면 [SecurityException],
     * 외부에서 받은 인텐트에 file:// 데이터가 실리면 [FileUriExposedException], 제조사 설정 앱에서는 그 밖의
     * 런타임 예외가 나므로 종류를 나열하지 않음. 놓치기 쉬운 실수인 NEW_TASK 누락은 이 함수가 막고,
     * 일시 중단 함수가 아니라 코루틴 취소 예외를 삼킬 일도 없음
     *
     * @return 시작 요청이 성공하면 true, 예외로 실패하면 false
     */
    fun startActivitySafely(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("start activity failed : $e")
            false
        }
    }
}