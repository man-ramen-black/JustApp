package com.black.feature.imagesearcher.ui.search

import androidx.lifecycle.MutableLiveData
import com.black.core.util.Log
import com.black.core.viewmodel.EventViewModel
import com.black.feature.imagesearcher.data.model.Content
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [SearchFragment]
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
//    private val model: SearchRepository
): EventViewModel() {
    companion object {
        const val EVENT_START_DETAIL = "EVENT_SHOW_DETAIL"
    }

    val isProgress = MutableLiveData(false)
    val isResultEmpty = MutableLiveData(false)
    val searchKeyword = MutableLiveData<String>()

//    private val favoriteFlow = model.getFavoriteFlow()
//    val searchFlow: Flow<PagingData<SearchItem>> = model.getSearchPagingFlow(searchKeyword.asFlow())
//        .map { paging ->
//            paging.map {
//                // PagingContent -> SearchItem.ContentItem으로 변환
//                SearchItem.ContentItem(
//                    it.content,
//                    it.page,
//                    // 좋아요 부분만 on/off되도록 flow -> liveData 적용
//                    favoriteFlow.map { favorite -> favorite.contains(it.content) }
//                        .asLiveData(),
//                    { content -> onClickContent(content) },
//                    { content -> onClickFavorite(content) },
//                ) as SearchItem
//            }
//        }
//        .map {
//            // 구분선 추가
//            it.insertSeparators { before, after ->
//                if (before !is SearchItem.ContentItem) {
//                    return@insertSeparators null
//                }
//
//                if (after == null) {
//                    SearchItem.PageDivider(SearchItem.PageDivider.PAGE_LAST)
//                } else if (after is SearchItem.ContentItem && after.page != before.page) {
//                    SearchItem.PageDivider(before.page)
//                } else {
//                    null
//                }
//            }
//        }
//        .cachedIn(viewModelScope)

    private fun onClickContent(content: Content) {
        Log.v(content)
        sendEvent(EVENT_START_DETAIL, content)
    }

    private fun onClickFavorite(content: Content) {
        Log.v(content)
        launchSingle {
//            model.toggleFavorite(content)
        }
    }

    fun onClickDelete() {
        searchKeyword.value = ""
    }
}