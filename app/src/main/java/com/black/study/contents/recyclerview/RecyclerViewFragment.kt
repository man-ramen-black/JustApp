package com.black.study.contents.recyclerview

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.black.study.R
import com.black.study.contents.ContentsFragment
import com.black.study.databinding.FragmentRecyclerviewBinding

class RecyclerViewFragment : ContentsFragment<FragmentRecyclerviewBinding>() {
    override val title: String = "RecyclerView"
    override val layoutResId: Int = R.layout.fragment_recyclerview
    private val adapter = RecyclerViewAdapter()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.recyclerView?.let {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = this@RecyclerViewFragment.adapter

            // RecyclerView는 divider 대신에 ItemDecoration을 사용... Interesting...
            // https://leveloper.tistory.com/180
            it.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        adapter.submitList(listOf("yoyo", "yapyap", "asdfasdf", "ehdgoanfrhk"))

    }
}