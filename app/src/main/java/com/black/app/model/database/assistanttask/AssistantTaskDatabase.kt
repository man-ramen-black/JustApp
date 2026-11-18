package com.black.app.model.database.assistanttask

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AssistantTaskEntity::class], version = 1, exportSchema = false)
abstract class AssistantTaskDatabase : RoomDatabase() {
    abstract fun dao(): AssistantTaskDao

    companion object {
        @Volatile private var instance: AssistantTaskDatabase? = null

        fun getInstance(context: Context): AssistantTaskDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext, AssistantTaskDatabase::class.java, "AssistantTask.db")
                .build()
    }
}
