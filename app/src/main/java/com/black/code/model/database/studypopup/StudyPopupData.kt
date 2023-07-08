package com.black.code.model.database.studypopup

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

sealed interface StudyPopupData {
    object FileManager : StudyPopupData

    @Entity
    data class Contents(@PrimaryKey var id: Int,
                        val contents: String) : StudyPopupData
    {
        @Ignore
        constructor(contents: String) : this(0, contents)
    }
}