package com.black.study

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

class MainAdapter() : RecyclerView.Adapter<MainAdapter.ViewHolder>(){

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var pid : TextView? = null
        var cid : TextView? = null
        var blockTime : TextView? = null
        var unblockTime : TextView? = null
        var blockButton : Button? = null
        var deleteButton : Button? = null

        init {
            pid = itemView.findViewById(R.id.pid)
            cid = itemView.findViewById(R.id.cid)
            blockTime = itemView.findViewById(R.id.blockTime)
            unblockTime = itemView.findViewById(R.id.unblockTime)
            blockButton = itemView.findViewById(R.id.blockButton)
            deleteButton = itemView.findViewById(R.id.deleteButton)
        }
    }

    private val dataList : ArrayList<BlockEntity> = ArrayList()
    private var onItemClickListener : AdapterView.OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_main, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        addOnItemClickListener(holder.itemView, position)
        getItem(position)?.let {
            holder.pid?.text = it.pid.toString()
            holder.cid?.text = it.cid.toString()
            holder.blockTime?.text = it.blockTime.toString()
            holder.unblockTime?.text = it.unblockTime.toString()
        }
    }

    override fun getItemCount(): Int = dataList.size

    fun getItem(position: Int) : BlockEntity?{
        if(!validatePosition(position)){
            return null
        }
        return dataList.get(position)
    }

    fun add(data : BlockEntity) = dataList.add(data)

    fun add(dataList : List<BlockEntity>) = this.dataList.addAll(dataList)

    fun removeAt(position : Int){
        if(!validatePosition(position)){
            return
        }
        dataList.removeAt(position)
    }

    fun clear() = dataList.clear()

    fun setOnItemClickListener(onItemClickListener: AdapterView.OnItemClickListener){
        this.onItemClickListener = onItemClickListener
    }

    //모든 뷰에 OnItemClickListener를 추가
    private fun addOnItemClickListener(view: View?, position: Int) {
        if (view == null || onItemClickListener == null){
            return
        }

        view.setOnClickListener { v -> onItemClickListener!!.onItemClick(null, view, position, view.id.toLong()) }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                addOnItemClickListener(view.getChildAt(i), position)
            }
        }
    }

    //position이 정상적인 값인지 검증
    private fun validatePosition(position: Int) : Boolean = position >= 0 && position < dataList.size
}