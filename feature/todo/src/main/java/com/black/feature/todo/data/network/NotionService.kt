package com.black.feature.todo.data.network

import com.black.core.network.Network
import com.black.core.network.NetworkLogLevel
import com.black.core.network.NetworkResult
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 노션 data source 조회, 페이지 추가, 페이지 수정 Retrofit 서비스
 *
 * `authorization`에는 [authorization]으로 만든 `Bearer <토큰>`을 넘기며, core [Network]의 로그는 이 헤더 값을 가림.
 * `dataSourceId`·`pageId`는 형식을 확인하지 않고 경로에 넣으므로 저장 전에 호출하는 쪽이 검증해야 함.
 */
interface NotionService {

    /** [dataSourceId]의 data source를 [request]로 조회 */
    @Headers(NOTION_VERSION_HEADER)
    @POST("v1/data_sources/{dataSourceId}/query")
    suspend fun queryDataSource(
        @Header("Authorization") authorization: String,
        @Path("dataSourceId") dataSourceId: String,
        @Body request: NotionQueryRequest,
    ): NetworkResult<NotionQueryResponse>

    /** [request]로 페이지 추가 */
    @Headers(NOTION_VERSION_HEADER)
    @POST("v1/pages")
    suspend fun createPage(
        @Header("Authorization") authorization: String,
        @Body request: NotionCreatePageRequest,
    ): NetworkResult<NotionPage>

    /** [pageId] 페이지의 속성을 [request]로 수정 */
    @Headers(NOTION_VERSION_HEADER)
    @PATCH("v1/pages/{pageId}")
    suspend fun updatePage(
        @Header("Authorization") authorization: String,
        @Path("pageId") pageId: String,
        @Body request: NotionUpdatePageRequest,
    ): NetworkResult<NotionPage>

    companion object {
        const val BASE_URL = "https://api.notion.com/"

        /** 요청 본문과 응답 형식을 정하는 노션 API 버전 */
        const val NOTION_VERSION = "2025-09-03"

        private const val NOTION_VERSION_HEADER = "Notion-Version: $NOTION_VERSION"

        /**
         * 앱 전체가 함께 쓰는 서비스
         *
         * [Network.service]는 부를 때마다 Retrofit을 새로 만들므로 한 번만 만듦.
         * 로그 수준 테스트가 [create]로 만든 서비스를 확인하므로 [create]로만 만듦.
         */
        val default: NotionService by lazy { create(BASE_URL) }

        /**
         * [baseUrl]로 요청하는 서비스
         *
         * 로그는 할 일 내용이 담긴 본문을 빼고 헤더까지만 남김.
         * 테스트는 MockWebServer 주소로 [default]와 같은 설정의 서비스를 만들 때 씀.
         */
        internal fun create(baseUrl: String): NotionService =
            Network.service(NotionService::class.java, baseUrl, NetworkLogLevel.HEADERS)

        /** [token]을 담은 `Authorization` 헤더 값 */
        fun authorization(token: String): String = "Bearer $token"
    }
}
