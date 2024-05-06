package com.black.app.model.database.studypopup

import android.content.Context
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

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                StudyPopupDatabase::class.java, "StudyPopup.db")
                .build()
    }
}