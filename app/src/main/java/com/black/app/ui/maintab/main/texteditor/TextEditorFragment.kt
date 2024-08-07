package com.black.app.ui.maintab.main.texteditor

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.black.app.R
import com.black.app.databinding.FragmentTextEditorBinding
import com.black.app.ui.common.base.TitleFragment
import com.black.core.file.SAFHelper
import com.black.core.util.FragmentExtension.launch
import com.black.core.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TextEditorFragment : TitleFragment<FragmentTextEditorBinding>(),
    com.black.core.viewmodel.EventObserver {

    override val layoutResId: Int
        get() = R.layout.fragment_text_editor

    override val title: String
        get() = "TextEditor"

    private val viewModel : TextEditorViewModel by viewModels()

    private val safHelper = SAFHelper(this, createMimeType = "text/plain")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safHelper.init()
    }

    override fun onBindVariable(binding: FragmentTextEditorBinding) {
        binding.viewModel = viewModel
        binding.titleProvider = this
        binding.navContoller = findNavController()
        viewModel.observeEvent(viewLifecycleOwner, this)
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        Log.d("action : $action, data : $data")
        when (action) {
            TextEditorViewModel.EVENT_LOAD -> load()

            TextEditorViewModel.EVENT_SAVE_NEW_FILE -> saveNewFile()

            TextEditorViewModel.EVENT_RESET -> confirmReset()

            TextEditorViewModel.EVENT_TOAST -> {
                val message = data as? String
                    ?: (data as? Int)?.let { getString(it) }
                    ?: ""
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun load() {
        launch {
            val uri = safHelper.load(arrayOf("text/plain"))
            viewModel.loadFile(uri ?: return@launch)
        }
    }

    private fun saveNewFile() {
        launch {
            val uri = safHelper.createFile(".txt")
            viewModel.saveFile(uri ?: return@launch)
        }
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireActivity())
            .setMessage(R.string.text_editor_new_file)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                viewModel.reset()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
}