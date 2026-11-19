package com.black.feature.studypopup.overlay

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.core.view.BaseViewHolder
import com.black.core.view.LoopingPagerAdapter
import com.black.feature.studypopup.R
import com.black.feature.studypopup.data.database.StudyPopupData
import com.black.feature.studypopup.databinding.ItemStudyPopupPagerContentsBinding

/**
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupPagerAdapter : LoopingPagerAdapter<StudyPopupData.Contents>() {
    class ViewHolder(binding: ItemStudyPopupPagerContentsBinding)
        : BaseViewHolder<ItemStudyPopupPagerContentsBinding, StudyPopupData.Contents>(binding) {
        override fun bind(item: StudyPopupData.Contents) {
            binding.data = item
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, StudyPopupData.Contents> {
        return ViewHolder(inflate(parent, R.layout.item_study_popup_pager_contents))
    }
}