package com.black.study

import androidx.room.Entity

@Entity(tableName = "Block")
data class BlockEntity (val pid:Int, val cid:Int, val blockTime:Long, val unblockTime:Long)