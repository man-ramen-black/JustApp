package com.black.app.ui.maintab.main.architecture

import android.content.Intent
import androidx.fragment.app.viewModels
import com.black.app.R
import com.black.app.databinding.FragmentArchitectureBinding
import com.black.app.ui.common.base.TitleFragment
import com.black.app.ui.maintab.main.architecture.mvc.MVCActivity
import com.black.app.ui.maintab.main.architecture.mvvm.MVVMActivity
import com.black.core.viewmodel.EventViewModel

class ArchitectureFragment : TitleFragment<FragmentArchitectureBinding>(),
    com.black.core.viewmodel.EventObserver {
    override val layoutResId: Int
        get() = R.layout.fragment_architecture

    override val title: String
        get() = "Architecture"

    private val viewModel : ViewModel by viewModels()

    override fun onBindVariable(binding: FragmentArchitectureBinding) {
        binding.viewModel = viewModel
        viewModel.observeEvent(viewLifecycleOwner, this)
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            "MVCActivity" -> {
                startActivity(Intent(requireContext(), MVCActivity::class.java))
            }
            "MVVMActivity" -> {
                startActivity(Intent(requireContext(), MVVMActivity::class.java))
            }
        }
    }

    class ViewModel : EventViewModel() {
        fun onClick(name: String) {
            sendEvent(name)
        }
    }
}