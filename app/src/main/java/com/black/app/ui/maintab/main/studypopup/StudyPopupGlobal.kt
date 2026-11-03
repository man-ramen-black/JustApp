package com.black.app.ui.maintab.main.studypopup

import android.content.Context
import android.content.Intent
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

object StudyPopupGlobal : ScreenReceiver.Interface {
    private var studyPopupView : StudyPopupView? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 화면이 켜질 때 표시 여부를 확인하고 팝업을 붙이는 작업. 확인 중에 화면이 꺼지면 취소 */
    private var showJob: Job? = null

    override fun onScreenOn(context: Context, intent: Intent) {
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
            studyPopupView = StudyPopupView(ContextThemeWrapper(context, R.style.Theme_Black)).also {
                it.attachView()
            }
        }
    }

    override fun onScreenOff(context: Context, intent: Intent) {
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
