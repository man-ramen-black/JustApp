package com.black.feature.pokerogue.ui

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.core.util.Log
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgItemDamageBinding
import com.black.feature.pokerogue.model.Damage
import com.black.feature.pokerogue.model.PokeType

data class DamageUiState(
    val damage: Damage,
    val typeList: List<PokeType>
)

class DamageAdapter: BaseListAdapter<DamageUiState>() {

    class ViewHolder(binding: PkrgItemDamageBinding): BaseViewHolder<PkrgItemDamageBinding, DamageUiState>(binding) {
        override fun bind(item: DamageUiState) {
            binding.damage = item.damage
            Log.e(item.typeList)
            binding.adapter = DamageTypeAdapter()
                .also { it.submitList(item.typeList) }
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, DamageUiState> {
        return ViewHolder(inflate(parent, R.layout.pkrg_item_damage))
    }
}