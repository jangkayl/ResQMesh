package com.example.testresqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.dao.NodeDao
import com.example.testresqmesh.data.local.entity.MessageEntity
import com.example.testresqmesh.data.local.entity.NodeEntity

@Database(entities = [NodeEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resqmesh_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
