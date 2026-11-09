package com.black.core.dialog

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.black.core.di.Hilt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

typealias BKAlertDialog = MaterialAlertDialogBuilder

/**
 * Activity 하나의 수명 동안 노출 중인 Dialog를 tag별로 관리
 *
 * 회전 등으로 Activity가 다시 만들어지면 새 인스턴스를 쓰고, 파괴되는 Activity의 Dialog는 모두 닫음
 * (회전 뒤에도 남는 범위로 두면 죽은 Dialog를 붙잡아 CANCEL은 영영 뜨지 않고 UPDATE는 크래시)
 */
@ActivityScoped
class DialogManager @Inject constructor(activity: Activity) {

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface DialogManagerEntryPoint {
        val dialogManager: DialogManager
    }

    init {
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                dismissAll()
            }
        })
    }

    /** 노출 방식 */
    enum class ShowType {
        /** 기본 동작, 중복으로 노출 */
        NORMAL,
        /** 동일 tag의 Dialog가 노출 중이면 있으면 닫고 노출 */
        UPDATE,
        /** 동일 tag의 Dialog가 노출 중이면 있으면 미노출 */
        CANCEL
    }

    companion object {
        fun AlertDialog.Builder.showByDialogManager(
            activity: ComponentActivity,
            tag: String = "",
            showType: ShowType = ShowType.NORMAL,
            onShow: ((DialogInterface) -> Unit)? = null,
            onDismiss: ((DialogInterface) -> Unit)? = null,
        ) { create().showByDialogManager(activity, tag, showType, onShow, onDismiss) }

        fun Dialog.showByDialogManager(
            activity: ComponentActivity,
            tag: String = "",
            showType: ShowType = ShowType.NORMAL,
            onShow: ((DialogInterface) -> Unit)? = null,
            onDismiss: ((DialogInterface) -> Unit)? = null,
        ) {
            Hilt.fromActivity<DialogManagerEntryPoint>(activity).dialogManager
                .show(
                    this,
                    tag,
                    showType,
                    onShow,
                    onDismiss
                )
        }
    }

    // 노출 중인 Dialog, <tag, ArrayList<Dialog>>
    private val showingDialogs = hashMapOf<String, MutableList<Dialog>>()

    fun isShowing(tag: String): Boolean
        = showingDialogs[tag]?.any { it.isShowing } == true

    /**
     * [dialog] 노출
     *
     * Dialog는 OnShow·OnDismiss 리스너를 하나씩만 가지므로 [onShow]·[onDismiss]를 넘긴 경우에만 지정
     * (넘기면 빌더에서 지정한 같은 종류의 리스너를 대체). 노출 목록은 리스너 대신 isShowing으로 정리
     */
    fun show(
        dialog: Dialog,
        tag: String = "",
        showType: ShowType = ShowType.NORMAL,
        onShow: ((DialogInterface) -> Unit)? = null,
        onDismiss: ((DialogInterface) -> Unit)? = null,
    ) {
        // onDismiss 처리가 되지 않는 일부 Dialog가 남아 있는 상황 방지를 위해 노출 중이지 않은 Dialog 제거
        removeNotShowingDialogs()

        if (showType != ShowType.NORMAL && isShowing(tag)) {
            when (showType) {
                ShowType.CANCEL -> {
                    return
                }
                ShowType.UPDATE -> {
                    dismiss(tag)
                }
                else -> {}
            }
        }

        onShow?.let { dialog.setOnShowListener(it) }
        onDismiss?.let { dialog.setOnDismissListener(it) }
        dialog.show()
        showingDialogs.getOrPut(tag) { mutableListOf() } += dialog
    }

    /** @param tag null인 경우 모든 Dialog */
    fun show(tag: String? = null) {
        if (tag == null) {
            showingDialogs.flatMap { it.value }
        } else {
            showingDialogs[tag]
        }?.forEach { it.show() }
    }

    /** @param tag null인 경우 모든 Dialog */
    fun hide(tag: String? = null) {
        if (tag == null) {
            showingDialogs.flatMap { it.value }
        } else {
            showingDialogs[tag]
        }?.forEach { it.hide() }
    }

    fun dismiss(tag: String) {
        showingDialogs[tag]?.forEach {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        showingDialogs.remove(tag)
    }

    fun dismissAll() {
        showingDialogs.forEach { (_, dialogList) ->
            dialogList.forEach {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
        }
        showingDialogs.clear()
    }

    /** 노출 중이지 않은 Dialog 제거 */
    private fun removeNotShowingDialogs() {
        showingDialogs.toMap().forEach { (key, dialogList) ->
            dialogList.toList().forEach {
                if (!it.isShowing) {
                    dialogList.remove(it)
                }
            }
            if (dialogList.isEmpty()) {
                showingDialogs.remove(key)
            }
        }
    }
}