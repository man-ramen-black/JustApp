package com.black.feature.imagesearcher.ui.search

import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.black.core.adapter.BasePagingAdapter
import com.black.core.view.BaseViewHolder
import com.black.feature.imagesearcher.R
import com.black.feature.imagesearcher.databinding.ImageSearcherItemSearchBinding
import com.black.feature.imagesearcher.databinding.ImageSearcherItemSearchDividerBinding

class SearchAdapter: BasePagingAdapter<SearchItem>({ old, new ->
    when {
        old is SearchItem.ContentItem && new is SearchItem.ContentItem -> {
            old.content == new.content
        }
        old is SearchItem.PageDivider && new is SearchItem.PageDivider -> {
            old.page == new.page
        }
        else -> false
    }
}) {
    companion object {
        private const val VIEW_TYPE_SEARCH_ITEM = 0
        private const val VIEW_TYPE_DIVIDER = 1
    }

    class SearchViewHolder(binding: ImageSearcherItemSearchBinding): BaseViewHolder<ImageSearcherItemSearchBinding, SearchItem>(binding) {
        override fun bind(item: SearchItem) {
            binding.item = item as SearchItem.ContentItem
        }
    }

    class DividerViewHolder(
        binding: ImageSearcherItemSearchDividerBinding
    ): BaseViewHolder<ImageSearcherItemSearchDividerBinding, SearchItem>(binding) {
        override fun bind(item: SearchItem) {
            binding.item = item as SearchItem.PageDivider
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchItem.ContentItem -> VIEW_TYPE_SEARCH_ITEM
            else -> VIEW_TYPE_DIVIDER
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ViewDataBinding, SearchItem> {
        return when (viewType) {
            VIEW_TYPE_SEARCH_ITEM -> SearchViewHolder(inflateForViewHolder(parent, R.layout.image_searcher_item_search))
            else -> DividerViewHolder(inflateForViewHolder(parent, R.layout.image_searcher_item_search_divider))
        }
    }
}