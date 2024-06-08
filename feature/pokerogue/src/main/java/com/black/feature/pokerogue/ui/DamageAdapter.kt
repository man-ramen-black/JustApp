package com.black.feature.pokerogue.ui

import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.databinding.ViewDataBinding
import com.black.core.util.Log
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgItemAttackBinding
import com.black.feature.pokerogue.databinding.PkrgItemDefenceBinding
import com.black.feature.pokerogue.databinding.PkrgItemDefenceListBinding
import com.black.feature.pokerogue.model.Damage
import com.black.feature.pokerogue.model.PokeType
import com.google.android.flexbox.FlexboxLayout

/** [PokeRogueFragment] */
data class DamageUiState(
    val damage: Damage,
    val typeList: List<PokeType>
)

data class DamageListUiState(
    val type: PokeType,
    val damageList: List<DamageUiState>
)

class AttackAdapter: BaseListAdapter<DamageUiState>() {
    class ViewHolder(binding: PkrgItemAttackBinding): BaseViewHolder<PkrgItemAttackBinding, DamageUiState>(binding) {
        override fun bind(item: DamageUiState) {
            binding.damage = item.damage
            binding.adapter = DamageTypeAdapter()
                .also { it.submitList(item.typeList) }
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, DamageUiState> {
        return ViewHolder(inflate(parent, R.layout.pkrg_item_attack))
    }
}

class DefenceListAdapter: BaseListAdapter<DamageListUiState>() {
    class ViewHolder(binding: PkrgItemDefenceListBinding): BaseViewHolder<PkrgItemDefenceListBinding, DamageListUiState>(binding) {
        override fun bind(item: DamageListUiState) {
            binding.adapter = DefenceAdapter()
                .also { it.submitList(item.damageList) }
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, DamageListUiState> {
        return ViewHolder(inflate(parent, R.layout.pkrg_item_defence_list))
    }
}

class DefenceAdapter: BaseListAdapter<DamageUiState>() {
    class ViewHolder(binding: PkrgItemDefenceBinding): BaseViewHolder<PkrgItemDefenceBinding, DamageUiState>(binding) {
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
        return ViewHolder(inflate(parent, R.layout.pkrg_item_defence))
    }
}