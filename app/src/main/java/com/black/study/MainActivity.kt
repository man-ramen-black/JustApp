package com.black.study

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity(), View.OnClickListener, AdapterView.OnItemClickListener {
    var adapter : MainAdapter? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = MainAdapter()
        listView.adapter = adapter
    }

    override fun onClick(view: View?) {
        when(view?.id){
            R.id.add -> {}
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

    }
}