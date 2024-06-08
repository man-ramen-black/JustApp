package com.black.feature.pokerogue.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.black.core.component.BaseFragment
import com.black.core.util.UiUtil
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgFragmentPokeRogueBinding
import com.black.feature.pokerogue.model.PokeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PokeRogueFragment: BaseFragment<PkrgFragmentPokeRogueBinding>() {

    private val typeList by lazy {
        PokeType.entries
            .map { it -> TypeUIState(it) { viewModel.onTypeClick(it.type) } }
    }

    private val viewModel: PokeRogueViewModel by viewModels()
    private lateinit var selectedTypeAdapter: TypeAdapter
    private lateinit var damageAdapter: DamageAdapter
    private lateinit var typeAdapter: TypeAdapter

    override val layoutResId: Int = R.layout.pkrg_fragment_poke_rogue

    override fun bindVariable(binding: PkrgFragmentPokeRogueBinding) {
        binding.viewModel = viewModel

        selectedTypeAdapter = TypeAdapter()
        binding.selectedTypeAdapter = selectedTypeAdapter
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.selectedTypes.collect { selectedTypeAdapter.submitList(it) }
        }

        damageAdapter = DamageAdapter()
        binding.damageAdapter = damageAdapter
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.damageItemList.collect { damageAdapter.submitList(it) }
        }

        typeAdapter = TypeAdapter().also { it.submitList(typeList) }
        binding.typeSelectAdapter = typeAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val window = requireActivity().window
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    UiUtil.setImmersiveMode(window, true)
                    UiUtil.setCutout(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS)
                }
                Lifecycle.Event.ON_RESUME -> {
                    binding.webView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    binding.webView.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    UiUtil.setImmersiveMode(window, false)
                    UiUtil.setCutout(window, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER)
                }
                else -> {}
            }
        })
    }
}