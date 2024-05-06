package com.black.app.ui.example.retrofit

import androidx.fragment.app.viewModels
import com.black.app.R
import com.black.app.databinding.FragmentRetrofitBinding
import com.black.app.model.network.sample.NetworkSampleModel
import com.black.app.ui.example.ExampleFragment

/**
 * Created by jinhyuk.lee on 2022/05/02
 **/
class RetrofitFragment : ExampleFragment<FragmentRetrofitBinding>() {
    override val title: String = "Retrofit"

    override val layoutResId: Int
        get() = R.layout.fragment_retrofit

    private val viewModel: RetrofitViewModel by viewModels()

    override fun bindVariable(binding: FragmentRetrofitBinding) {
        binding.fragment = this
        binding.viewModel = viewModel.apply {
            setModel(NetworkSampleModel(requireContext()))
        }
    }
}