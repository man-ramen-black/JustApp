package com.black.code.util

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogUtil {
    fun showListDialog(context: Context, title: String, items: Array<String>, onItemClick: (dialog: DialogInterface, which: Int, item: String) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(items) { dialog, which ->
                onItemClick(dialog, which, items[which])
            }
            .show()
    }
}