package com.black.code.contents.architecture

import android.content.Intent
import androidx.fragment.app.viewModels
import com.black.code.R
import com.black.code.base.viewmodel.EventObserver
import com.black.code.base.viewmodel.EventViewModel
import com.black.code.contents.ContentsFragment
import com.black.code.contents.architecture.mvc.MVCActivity
import com.black.code.contents.architecture.mvvm.MVVMActivity
import com.black.code.databinding.FragmentArchitectureBinding

class ArchitectureFragment : ContentsFragment<FragmentArchitectureBinding>(), EventObserver {
    override val layoutResId: Int
        get() = R.layout.fragment_architecture

    override val title: String
        get() = "Architecture"

    private val viewModel : ViewModel by viewModels()

    override fun bindVariable(binding: FragmentArchitectureBinding) {
        binding.viewModel = viewModel
        viewModel.event.observe(this, this)
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
            event.send(name)
        }
    }
}