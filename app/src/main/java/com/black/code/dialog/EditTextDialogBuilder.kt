package com.black.code.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.black.code.R
import com.black.code.util.Util
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Created by jinhyuk.lee on 2022/05/23
 **/
class EditTextDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {
    private val view by lazy { LayoutInflater.from(context).inflate(R.layout.view_edit_text_dialog, null) }
    private val editText by lazy { view.findViewById<EditText>(R.id.edit_text)}

    override fun create(): AlertDialog {
        super.setView(view)
        return super.create()
    }

    fun setEditText(text: String) : EditTextDialogBuilder {
        editText.setText(text)
        return this
    }

    fun setNeutralButton(
        textId: Int,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setNeutralButton(textId) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    fun setNeutralButton(
        text: CharSequence?,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setNeutralButton(text) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    fun setPositiveButton(
        textId: Int,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setPositiveButton(textId) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    fun setPositiveButton(
        text: CharSequence?,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setPositiveButton(text) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    fun setNegativeButton(
        textId: Int,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setNegativeButton(textId) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    fun setNegativeButton(
        text: CharSequence?,
        listener: ((dialog: DialogInterface, which: Int, text: String) -> Unit)?
    ): EditTextDialogBuilder {
        super.setNegativeButton(text) { dialog, which ->
            listener?.invoke(dialog, which, editText.text.toString())
        }
        return this
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setView(layoutResId: Int): MaterialAlertDialogBuilder {
        return super.setView(layoutResId)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setView(view: View?): MaterialAlertDialogBuilder {
        return super.setView(view)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setNeutralButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setNeutralButton(textId, listener)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setNeutralButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setNeutralButton(text, listener)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setPositiveButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setPositiveButton(textId, listener)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setPositiveButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setPositiveButton(text, listener)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setNegativeButton(
        textId: Int,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setNegativeButton(textId, listener)
    }

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    override fun setNegativeButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): MaterialAlertDialogBuilder {
        return super.setNegativeButton(text, listener)
    }
}