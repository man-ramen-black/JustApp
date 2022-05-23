package com.black.code.ui.example.studypopup

/**
 * Created by jinhyuk.lee on 2022/05/10
 **/
sealed class StudyPopupData {
    class FileManager : StudyPopupData()
    data class Contents(val text: String) : StudyPopupData()
}