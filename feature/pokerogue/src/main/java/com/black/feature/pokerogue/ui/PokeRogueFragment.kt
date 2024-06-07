package com.black.feature.pokerogue.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.eventFlow
import com.black.core.component.BaseFragment
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding

class PokeRogueFragment: BaseFragment<PkrgFragmentPokeRogueBinding>() {

    private val viewModel: PokeRogueViewModel by viewModels()

    override val layoutResId: Int = R.layout.pkrg_fragment_poke_rogue

    override fun bindVariable(binding: PkrgFragmentPokeRogueBinding) {
        binding.viewModel = viewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { owner, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    binding.webView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    binding.webView.onPause()
                }
                else -> {}
            }
        })
    }
}