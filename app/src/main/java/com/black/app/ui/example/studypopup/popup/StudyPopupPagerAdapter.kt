package com.black.app.ui.example.studypopup.popup

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.app.R
import com.black.core.view.LoopingPagerAdapter
import com.black.app.databinding.ItemStudyPopupPagerContentsBinding
import com.black.app.model.database.studypopup.StudyPopupData

/**
 * Created by jinhyuk.lee on 2022/05/24
 **/
class StudyPopupPagerAdapter : com.black.core.view.LoopingPagerAdapter<StudyPopupData.Contents>() {
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
        return ViewHolder(inflateForViewHolder(parent, R.layout.item_study_popup_pager_contents))
    }
}