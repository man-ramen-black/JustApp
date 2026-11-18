package com.black.core.model

import com.black.core.util.JsonUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * 오버레이 창의 표시 위치(붙일 가장자리와 여백)
 *
 * 여백은 dp 단위이며 [horizontal]·[vertical]로 붙은 쪽의 값만 배치에 쓰임.
 * 가운데 기준일 때는 양쪽 여백의 차이만큼 가운데에서 밀림.
 */
@Serializable
data class OverlayPosition(
    val horizontal: HorizontalAnchor = HorizontalAnchor.LEFT,
    val vertical: VerticalAnchor = VerticalAnchor.TOP,
    val marginTopDp: Int = 0,
    val marginBottomDp: Int = 0,
    val marginLeftDp: Int = 0,
    val marginRightDp: Int = 0,
) {
    /** 가로 기준 */
    enum class HorizontalAnchor { LEFT, CENTER, RIGHT }

    /** 세로 기준 */
    enum class VerticalAnchor { TOP, CENTER, BOTTOM }

    /**
     * 한 줄 JSON 문자열로 변환
     *
     * 변환에 실패하면 null 반환
     */
    fun toJson(): String? = JsonUtil.to(this, isKotlinSerialize = true, kotlinJson = JsonUtil.kotlinSerializationCompact)

    /**
     * JSON 문자열로 바꿔 [save]로 저장
     *
     * DataStore가 자기 키 저장 함수를 넘겨 호출. JSON 변환에 실패하면 [save]를 호출하지 않고 실패 결과 반환
     */
    suspend fun saveAsJson(save: suspend (json: String) -> Result<Unit>): Result<Unit> {
        val json = toJson()
            ?: return Result.failure(IllegalStateException("표시 위치를 JSON으로 변환하지 못했습니다: $this"))
        return save(json)
    }

    companion object {
        /** [json]을 복원하고 null이거나 파싱에 실패하면 [default] 반환 */
        fun fromJson(json: String?, default: OverlayPosition): OverlayPosition {
            json ?: return default
            return JsonUtil.from<OverlayPosition>(json, isKotlinSerialize = true) ?: default
        }

        /**
         * 저장된 JSON 문자열 Flow를 위치 Flow로 변환
         *
         * DataStore가 자기 키의 Flow를 넘겨 호출. 값마다 [fromJson]으로 복원하고(없거나 복원 실패 시 [default]),
         * 같은 위치는 다시 내보내지 않음. DataStore Flow는 같은 저장소의 다른 키가 저장돼도 다시 방출하므로,
         * 이 처리가 없으면 끌어서 옮긴 오버레이가 위치와 무관한 저장 때 설정 위치로 되돌아감
         */
        fun fromJsonFlow(jsonFlow: Flow<String?>, default: OverlayPosition): Flow<OverlayPosition> {
            return jsonFlow
                .map { fromJson(it, default) }
                .distinctUntilChanged()
        }
    }
}
