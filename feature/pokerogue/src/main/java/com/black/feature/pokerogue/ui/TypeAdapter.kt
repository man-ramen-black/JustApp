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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypeUIState

        return type == other.type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

}

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