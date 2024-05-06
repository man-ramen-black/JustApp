package com.black.app.ui.main.studypopup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.app.R
import com.black.app.databinding.FragmentStudyPopupBinding
import com.black.app.util.InputDialogBuilder
import com.black.app.model.StudyPopupModel
import com.black.app.model.database.studypopup.StudyPopupData
import com.black.app.ui.common.base.TitleFragment
import com.black.app.ui.main.studypopup.popup.StudyPopupView
import com.black.app.util.FileUtil
import com.black.core.util.Log
import com.black.core.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Created by jinhyuk.lee on 2022/05/22
 **/
class StudyPopupFragment : TitleFragment<FragmentStudyPopupBinding>(),
    com.black.core.viewmodel.EventObserver {

    companion object {
        private val PERMISSIONS = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override val layoutResId: Int
        get() = R.layout.fragment_study_popup

    override val title: String
        get() = "StudyPopup"

    private val viewModel : StudyPopupFragmentViewModel by viewModels()

    private lateinit var openDocumentLauncher : ActivityResultLauncher<Array<String>>
    private lateinit var createDocumentLauncher : ActivityResultLauncher<String>

    private val permissionHelper by lazy { PermissionHelper(requireActivity(), this) }

    private val adapter by lazy { StudyPopupListAdapter(viewModel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHelper.init()

        // registerForActivityResult는 onAttach 또는 onCreate에서 호출되어야 함
        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument(), this::onOpenedDocument)
        createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(), this::onCreatedDocument)
    }

    override fun bindVariable(binding: FragmentStudyPopupBinding) {
        binding.viewModel = viewModel.apply {
            setModel(StudyPopupModel(requireContext()))
        }
        viewModel.observeEvent(this, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.recyclerView?.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding?.recyclerView?.adapter = adapter
        viewModel.loadLatestFile()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            StudyPopupFragmentViewModel.EVENT_LOAD -> {
                load()
            }

            StudyPopupFragmentViewModel.EVENT_LOAD_LATEST -> {
                onOpenedDocument(data as Uri)
            }

            StudyPopupFragmentViewModel.EVENT_SAVE_NEW_DOCUMENT -> {
                saveNewFile()
            }

            StudyPopupFragmentViewModel.EVENT_SAVE_OVERWRITE -> {
                saveOverwrite()
            }

            StudyPopupFragmentViewModel.EVENT_UPDATE_RECYCLER_VIEW -> {
                adapter.submitList(data as List<StudyPopupData>)
            }

            StudyPopupFragmentViewModel.EVENT_EDIT_CONTENTS -> {
                showEditDialog(data as StudyPopupFragmentViewModel.IndexedContents)
            }

            StudyPopupFragmentViewModel.EVENT_TOAST -> {
                Toast.makeText(requireContext(), data?.toString() ?: "", Toast.LENGTH_SHORT).show()
            }

            StudyPopupFragmentViewModel.EVENT_ATTACH_VIEW -> {
                StudyPopupView(requireContext()).attachView()
            }

            StudyPopupFragmentViewModel.EVENT_CONFIRM_DELETE -> {
                android.R.drawable.ic_btn_speak_now
                confirmDelete(data as Int)
            }
        }
    }

    /**
     * @callback [onOpenedDocument]
     */
    private fun load() {
        requestPermission {
            openDocumentLauncher.launch(arrayOf("text/xml", "application/xml"))
        }
    }

    private fun onOpenedDocument(uri: Uri?) {
        uri ?: run {
            Log.w("uri is null")
            return
        }

        val inputStream = requireActivity().contentResolver.apply {
            // 재부팅 시에도 해당 URI의 권한이 유지되도록 설정
            takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.openInputStream(uri)

        val path = FileUtil.getPath(requireContext(), uri)
        viewModel.loadFile(uri, path, inputStream)
    }

    /**
     * @callback [onCreatedDocument]
     */
    private fun saveNewFile() {
        requestPermission {
            createDocumentLauncher.launch(".xml")
        }
    }

    private fun onCreatedDocument(uri: Uri?) {
        uri ?: run {
            Log.w("uri is null")
            return
        }

        // wt : 스트림을 열면서 해당 파일 내용을 지움
        val outputStream = requireActivity().contentResolver.openOutputStream(uri, "wt")
        val path = FileUtil.getPath(requireContext(), uri)
        viewModel.saveNewFile(uri, path, outputStream)
    }

    private fun saveOverwrite() {
        val openedFileUri = viewModel.openedFileUri ?: run {
            Toast.makeText(requireContext(), "openedFileUri is null", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val stream = requireActivity().contentResolver.openOutputStream(openedFileUri, "wt")
        viewModel.saveOverwrite(openedFileUri, stream)
    }

    private fun showEditDialog(data: StudyPopupFragmentViewModel.IndexedContents) {
        InputDialogBuilder(requireActivity())
            .setPositiveButton("확인") { dialog, _, text ->
                viewModel.updateItem(data.index, StudyPopupData.Contents(text))
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _, _ ->
                dialog.dismiss()
            }
            .setEditText(data.data.contents)
            .setTitle("내용 입력")
            .show()
    }

    private fun requestPermission(onPermissionGranted : () -> Unit) {
        permissionHelper.requestPermissions(PERMISSIONS) {
            if (it.isGranted) {
                onPermissionGranted()
            } else {
                Toast.makeText(requireContext(), "권한 허용 필요", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun confirmDelete(position: Int) {
        MaterialAlertDialogBuilder(requireActivity())
            .setMessage("삭제하시겠습니까?")
            .setPositiveButton("확인") { dialog, _ ->
                viewModel.deleteItem(position)
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}