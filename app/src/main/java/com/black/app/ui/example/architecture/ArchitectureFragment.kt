package com.black.app.ui.example.architecture

import android.content.Intent
import androidx.fragment.app.viewModels
import com.black.app.R
import com.black.app.base.viewmodel.EventObserver
import com.black.app.base.viewmodel.EventViewModel
import com.black.app.ui.example.architecture.mvc.MVCActivity
import com.black.app.ui.example.architecture.mvvm.MVVMActivity
import com.black.app.databinding.FragmentArchitectureBinding
import com.black.app.ui.example.ExampleFragment

class ArchitectureFragment : ExampleFragment<FragmentArchitectureBinding>(), EventObserver {
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