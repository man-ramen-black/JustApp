package com.black.app.ui.main.calculator

import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel

/**
 * 계산기 ViewModel의 저장값 복원과 사용자 변경 저장 도우미
 *
 * 저장값을 읽는 중에 사용자가 값을 바꾸면 읽은 값을 버려 사용자 입력을 우선.
 * 코루틴은 [launcher]로 받은 ViewModel의 [EventViewModel.launch]에서 실행.
 *
 * @param launcher 블록을 ViewModel 코루틴으로 실행하는 함수
 * @param load 저장값 조회
 * @param save 값 저장(실패해도 상태는 유지하고 로그만 남김)
 * @param saveLogName 저장 실패 로그 앞에 붙일 저장 함수 이름
 * @param updateState 값을 화면 상태에 반영
 */
class PersistedUserInput<T>(
    private val launcher: (suspend () -> Unit) -> Unit,
    private val load: suspend () -> T,
    private val save: suspend (T) -> Result<Unit>,
    private val saveLogName: String,
    private val updateState: (T) -> Unit,
) {

    /**
     * 저장값을 읽는 중에 사용자가 값을 바꿨는지 여부
     *
     * true면 읽은 값을 버림.
     */
    private var isChangedByUser = false

    /** 저장값을 읽어 상태에 반영(읽는 중에 사용자가 바꿨으면 버림) */
    fun restore() {
        launcher {
            val savedValue = load()
            if (!isChangedByUser) updateState(savedValue)
        }
    }

    /** 사용자가 바꾼 [value]를 바로 상태에 반영하고 저장 */
    fun change(value: T) {
        isChangedByUser = true
        updateState(value)
        launcher {
            save(value).onFailure { Log.w("$saveLogName failed: $it") }
        }
    }
}
