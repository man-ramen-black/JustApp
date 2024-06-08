package com.black.feature.pokerogue.ui

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.core.view.BaseListAdapter
import com.black.core.view.BaseViewHolder
import com.black.feature.pokerogue.R
import com.black.feature.pokerogue.databinding.PkrgItemTypeBinding
import com.black.feature.pokerogue.model.PokeType

data class TypeUIState(
    val type: PokeType,
    val onClick: (TypeUIState) -> Unit
)

/** [PokeRogueFragment] */
class TypeAdapter: BaseListAdapter<TypeUIState>() {

    class ViewHolder(binding: PkrgItemTypeBinding): BaseViewHolder<PkrgItemTypeBinding, TypeUIState>(binding) {
        override fun bind(item: TypeUIState) {
            binding.uiState = item
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, TypeUIState> {
        return ViewHolder(inflate(parent, R.layout.pkrg_item_type))
    }
}