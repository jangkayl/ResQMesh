package com.example.testresqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.testresqmesh.data.local.dao.AttachmentDao
import com.example.testresqmesh.data.local.entity.AttachmentEntity
import com.example.testresqmesh.data.local.entity.AttachmentTransferEntity
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.dao.NodeDao
import com.example.testresqmesh.data.local.entity.MessageEntity
import com.example.testresqmesh.data.local.entity.NodeEntity

@Database(entities = [NodeEntity::class, MessageEntity::class, AttachmentEntity::class, AttachmentTransferEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentId TEXT")
                database.execSQL("CREATE TABLE IF NOT EXISTS attachments (attachmentId TEXT NOT NULL, messageId TEXT NOT NULL, peerName TEXT NOT NULL, directedRouteNodeIds TEXT NOT NULL, isOutgoing INTEGER NOT NULL, status TEXT NOT NULL, byteSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, previewPath TEXT, localPath TEXT, tempPath TEXT, keyMaterialBase64 TEXT, receivedBytes INTEGER NOT NULL, totalChunks INTEGER NOT NULL, sourceExpiresAt INTEGER NOT NULL, failureReason TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(attachmentId))")
                database.execSQL("CREATE TABLE IF NOT EXISTS attachment_transfers (attachmentId TEXT NOT NULL, peerNodeId TEXT NOT NULL, nextChunk INTEGER NOT NULL, state TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(attachmentId, peerNodeId))")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resqmesh_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }

}
