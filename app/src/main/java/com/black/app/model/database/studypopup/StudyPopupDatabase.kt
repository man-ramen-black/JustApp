package com.black.app.model.database.studypopup

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StudyPopupData.Contents::class], version = 1)
abstract class StudyPopupDatabase : RoomDatabase() {
    abstract fun dao(): StudyPopupDao

    companion object{
        /*
        #Singleton
         */
        @Volatile private var instance: StudyPopupDatabase? = null

        fun getInstance(context: Context): StudyPopupDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        /**
         * 단일 인스턴스를 닫고 비움, 다음 [getInstance]는 새 연결로 생성
         *
         * Robolectric LEGACY SQLite 모드(Windows 기본)가 테스트 종료 때 연결을 정리해 다음 테스트로 넘긴 인스턴스가 실패하므로,
         * 테스트마다 연결이 유효한 동안 호출
         */
        @VisibleForTesting
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                StudyPopupDatabase::class.java, "StudyPopup.db")
                .build()
    }
}
