package com.example.testresqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.dao.NodeDao
import com.example.testresqmesh.data.local.entity.MessageEntity
import com.example.testresqmesh.data.local.entity.NodeEntity

@Database(entities = [NodeEntity::class, MessageEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Versions 2 and 3 briefly stored private-image metadata. This is intentionally a
         * forward migration: rebuilding messages removes only the retired attachmentId column
         * while retaining every chat row, then drops the retired attachment tables.
        */
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = removeLegacyAttachmentSchema(db)
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = removeLegacyAttachmentSchema(db)
        }

        /** Version 1 already has the current schema; this only advances its schema identity. */
        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        private fun removeLegacyAttachmentSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS messages_new (msgId TEXT NOT NULL, senderName TEXT NOT NULL, targetName TEXT, text TEXT, imageBase64 TEXT, audioBase64 TEXT, locationLat REAL, locationLng REAL, timestamp INTEGER NOT NULL, isSOS INTEGER NOT NULL, isMine INTEGER NOT NULL, deliveredTo TEXT NOT NULL, seenBy TEXT NOT NULL, outboundRoute TEXT NOT NULL, PRIMARY KEY(msgId))"
            )
            database.execSQL(
                "INSERT INTO messages_new (msgId, senderName, targetName, text, imageBase64, audioBase64, locationLat, locationLng, timestamp, isSOS, isMine, deliveredTo, seenBy, outboundRoute) SELECT msgId, senderName, targetName, text, imageBase64, audioBase64, locationLat, locationLng, timestamp, isSOS, isMine, deliveredTo, seenBy, outboundRoute FROM messages"
            )
            database.execSQL("DROP TABLE messages")
            database.execSQL("ALTER TABLE messages_new RENAME TO messages")
            database.execSQL("DROP TABLE IF EXISTS attachment_transfers")
            database.execSQL("DROP TABLE IF EXISTS attachments")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resqmesh_database"
                ).addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
