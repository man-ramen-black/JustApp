package com.black.core.dialog

import android.app.Dialog
import android.content.DialogInterface
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.black.core.di.Hilt
import com.black.core.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

typealias BKAlertDialog = MaterialAlertDialogBuilder

// Activity 생명 주기 동안 Singleton
@ActivityRetainedScoped
class DialogManager @Inject constructor() {

    @dagger.hilt.EntryPoint
    @InstallIn(ActivityComponent::class)
    interface EntryPoint {
        @get:ActivityRetainedScoped
        val dialogManager: DialogManager
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
            onShow: (DialogInterface) -> Unit = {},
            onDismiss: (DialogInterface) -> Unit = {},
        ) { create().showByDialogManager(activity, tag, showType, onShow, onDismiss) }

        fun Dialog.showByDialogManager(
            activity: ComponentActivity,
            tag: String = "",
            showType: ShowType = ShowType.NORMAL,
            onShow: (DialogInterface) -> Unit = {},
            onDismiss: (DialogInterface) -> Unit = {},
        ) {
            Hilt.fromActivity<EntryPoint>(activity).dialogManager
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
    private var isNoShow = false

    fun isShowing(tag: String): Boolean
        = showingDialogs[tag]?.any { it.isShowing } == true

    fun show(
        dialog: Dialog,
        tag: String = "",
        showType: ShowType = ShowType.NORMAL,
        onShow: (DialogInterface) -> Unit = {},
        onDismiss: (DialogInterface) -> Unit = {}
    ) {
        if (isNoShow) {
            Log.v("No show")
            return
        }

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

        dialog.setOnShowListener {
            this.onShow(dialog, tag)
            onShow(it)
        }

        dialog.setOnDismissListener {
            this.onDismiss(it)
            onDismiss(it)
        }

        dialog.show()
    }

    /**
     * DialogManager.show가 동작하지 않도록 설정
     */
    fun setNoShow(isNoShow: Boolean) {
        Log.v(isNoShow)
        this.isNoShow = isNoShow
    }

    private fun onShow(dialog: Dialog, tag: String) {
        // onDismiss 처리가 되지 않는 일부 Dialog가 남아 있는 상황 방지를 위해 노출 중이지 않은 Dialog 제거
        removeNotShowingDialogs()
        showingDialogs.getOrPut(tag) { mutableListOf() } += dialog
    }

    private fun onDismiss(dialog: DialogInterface) {
        showingDialogs.toMap()
            .forEach { (tag, dialogList) ->
                dialogList.remove(dialog)
                if (showingDialogs[tag]!!.isEmpty()) {
                    showingDialogs.remove(tag)
                }
            }
    }

    /**
     * @param tag null인 경우 모든 Dialog
     */
    fun show(tag: String? = null) {
        if (tag == null) {
            showingDialogs.flatMap { it.value }
        } else {
            showingDialogs[tag]
        }?.forEach { it.show() }
    }

    /**
     * @param tag null인 경우 모든 Dialog
     */
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