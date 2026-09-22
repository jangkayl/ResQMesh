package com.example.testresqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.dao.NodeDao
import com.example.testresqmesh.data.local.dao.UserDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.MessageEntity
import com.example.testresqmesh.data.local.entity.NodeEntity
import com.example.testresqmesh.data.local.entity.UserEntity

@Database(
    entities = [
        NodeEntity::class,
        MessageEntity::class,
        UserEntity::class,
        IncidentEntity::class,
        DomainEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun incidentDao(): IncidentDao
    abstract fun domainEventDao(): DomainEventDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`userId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `publicKey` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `incidents` (`incidentId` TEXT NOT NULL, `creatorId` TEXT NOT NULL, `creatorName` TEXT NOT NULL, `incidentType` TEXT NOT NULL, `severity` TEXT NOT NULL, `description` TEXT NOT NULL, `areaDescription` TEXT NOT NULL, `status` TEXT NOT NULL, `primaryResponderId` TEXT, `primaryResponderName` TEXT, `version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`incidentId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `domain_events` (`eventId` TEXT NOT NULL, `entityId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `eventType` TEXT NOT NULL, `actorId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `logicalVersion` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, `signature` TEXT, `applied` INTEGER NOT NULL, PRIMARY KEY(`eventId`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE incidents ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE incidents ADD COLUMN locationCapturedAt INTEGER")
                db.execSQL("ALTER TABLE incidents ADD COLUMN locationAccuracyMeters REAL")
            }
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
                ).addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
