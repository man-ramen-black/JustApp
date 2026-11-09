package com.black.app.ui.maintab.main.studypopup

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.annotation.VisibleForTesting
import com.black.app.R
import com.black.app.broadcast.ScreenReceiver
import com.black.app.model.StudyPopupRepository
import com.black.app.ui.maintab.main.studypopup.popup.StudyPopupView
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 화면 켜짐([ScreenReceiver])과 설정 화면 미리보기에서 스터디 팝업 표시 */
object StudyPopupGlobal {
    private var studyPopupView : StudyPopupView? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 화면이 켜질 때 표시 여부를 확인하고 팝업을 붙이는 작업. 확인 중에 화면이 꺼지면 취소 */
    private var showJob: Job? = null

    fun onScreenOn(context: Context) {
        // 다른 앱 위에 표시 권한 없이 오버레이 창을 추가하면 화면이 켜질 때마다 서비스 프로세스가 크래시
        if (!PermissionHelper.canDrawOverlays(context)) {
            Log.w("cannot draw overlays")
            return
        }
        showJob?.cancel()
        showJob = mainScope.launch {
            // 표시 여부 확인에 DB 조회가 필요해 확인을 마친 뒤 부착
            if (!canShow(StudyPopupRepository(context))) {
                return@launch
            }
            // 미리보기 등으로 이미 떠 있으면 겹쳐 붙이지 않음
            if (studyPopupView?.isAttachedToWindow == true) {
                return@launch
            }
            // 확인하는 동안 권한이 회수되면 창 추가가 거부되므로 실패는 부착하지 않은 것으로 처리
            val view = StudyPopupView(ContextThemeWrapper(context, R.style.Theme_Black))
            if (view.tryAttachView()) {
                studyPopupView = view
            }
        }
    }

    /**
     * 설정 화면에서 팝업 미리보기 표시(이미 떠 있으면 다시 붙이지 않음)
     *
     * TYPE_APPLICATION_OVERLAY 창은 Activity가 파괴돼도 걷히지 않아 Activity context로 만들면 Activity를 붙잡으므로 applicationContext로 생성
     */
    fun showPreview(context: Context) {
        if (studyPopupView?.isAttachedToWindow == true) {
            return
        }
        val view = StudyPopupView(ContextThemeWrapper(context.applicationContext, R.style.Theme_Black))
        if (view.tryAttachView()) {
            studyPopupView = view
        }
    }

    fun onScreenOff() {
        // 표시 여부 확인 중에 화면이 꺼지면 꺼진 화면에 팝업이 붙지 않도록 취소
        showJob?.cancel()
        showJob = null
        studyPopupView?.detachView()
        studyPopupView = null
    }

    /**
     * 화면이 켜질 때 팝업 표시 여부
     *
     * 표시 설정이 꺼져 있으면(기본값) 표시하지 않고, 등록된 내용이 없으면 빈 팝업이 되므로 표시하지 않음
     */
    @VisibleForTesting
    internal suspend fun canShow(repository: StudyPopupRepository): Boolean {
        if (!repository.isEnabled()) {
            Log.d("study popup disabled")
            return false
        }
        if (!repository.hasContents()) {
            Log.d("study popup contents empty")
            return false
        }
        return true
    }
}
