package com.black.feature.pokerogue.ui

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgItemDamageTypeBinding
import com.black.feature.pokerogue.model.PokeType

class DamageTypeAdapter: BaseListAdapter<PokeType>() {
    class ViewHolder(binding: PkrgItemDamageTypeBinding): BaseViewHolder<PkrgItemDamageTypeBinding, PokeType>(binding) {
        override fun bind(item: PokeType) {
            binding.type = item
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, PokeType> {
        return ViewHolder(inflate(parent, R.layout.pkrg_item_damage_type))
    }
}