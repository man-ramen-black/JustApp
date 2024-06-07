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

import android.webkit.CookieManager
import com.black.core.util.Log
import java.net.URLEncoder

/**
 * 쿠키 처리용 유틸
 * created by jinhyuk.lee on 2020.02.25
 */
object CookieUtil {

    /**
     * 쿠키 셋팅
     */
    fun setCookie(domain: String, values: Map<String, String?>) {
        setCookie(domain, "", values)
    }

    fun setCookie(domain: String, path: String, values: Map<String, String?>) {
        //쿠키 등록
        for((key, value) in values) {
            setCookieInternal(domain, path, key, value)
        }

        //쿠키 동기화
        sync()
    }

    fun removeAllCookies() {
        CookieManager.getInstance().removeAllCookies { Log.d(it) }

        //쿠키 동기화
        sync()
    }

    private fun setCookieInternal(domain: String, path: String, key: String, value: String?) {
        // 도메인에 http:// 등이 포함되어 있으면 쿠키 설정이 정상적으로 되지 않기 때문에 :// 뒷부분만 사용
        val validDomain = domain.substringAfter("://")

        // path의 시작이 / 가 아니면 맨 앞에 / 를 붙여줌 (/로 시작하지 않으면 정상동작 불가)
        val validPath = if (path.startsWith("/")) path else "/$path"

        try {
            if(value == null){
                Log.d("$key | value is null : remove cookie")
                String.format("%s=; path=%s; domain=%s; expires=Sat, 1 Jan 2000 00:00:01 UTC", key, validPath, validDomain)
            } else {
                //쿠키 생성
                String.format("%s=%s; path=%s; domain=%s; sessionOnly=TRUE;", key, URLEncoder.encode(value, "UTF-8"), validPath, validDomain)
            }
        } catch (e: Exception) {
            Log.w("${e.message}\nkey : $key, value : $value, domain: $validDomain, path: $validPath")
            null
        }?.let {
            //쿠키 설정
            CookieManager.getInstance().setCookie("$validDomain$validPath", it)
        }
    }

    /** 웹뷰 쿠키 동기화 */
    fun sync(){
        /*
        간헐적으로 아래와 같은 원인 불명 에러 발생으로 Exception 처리
        android.util.AndroidRuntimeException: android.webkit.WebViewFactory$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed
         */
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}