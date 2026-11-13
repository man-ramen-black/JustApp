package com.black.app.model.database.usagelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UsageLogEntity::class], version = 1, exportSchema = false)
abstract class UsageLogDatabase : RoomDatabase() {
    abstract fun dao(): UsageLogDao

    companion object {
        @Volatile private var instance: UsageLogDatabase? = null

        fun getInstance(context: Context): UsageLogDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext, UsageLogDatabase::class.java, "UsageLog.db")
                .build()
    }
}
