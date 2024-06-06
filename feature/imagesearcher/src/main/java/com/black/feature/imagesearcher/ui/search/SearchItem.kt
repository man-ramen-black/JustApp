package com.black.feature.imagesearcher.ui.search

import androidx.lifecycle.LiveData
import com.black.feature.imagesearcher.data.model.Content

sealed class SearchItem {
    data class ContentItem(
        val content: Content,
        val page: Int,
        val isFavorite: LiveData<Boolean>,
        val onItemClick: (Content) -> Unit,
        val onClickFavorite: (Content) -> Unit,
    ) : SearchItem()

    data class PageDivider(val page: Int) : SearchItem() {
        companion object {
            const val PAGE_LAST = -1
        }
    }
}
