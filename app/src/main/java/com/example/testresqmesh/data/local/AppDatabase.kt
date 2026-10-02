package com.example.testresqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.dao.IncidentDao
import com.example.testresqmesh.data.local.dao.IncidentOfferDao
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.dao.NodeDao
import com.example.testresqmesh.data.local.dao.PeerNameDao
import com.example.testresqmesh.data.local.dao.UserDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.MessageEntity
import com.example.testresqmesh.data.local.entity.NodeEntity
import com.example.testresqmesh.data.local.entity.PeerNameEntity
import com.example.testresqmesh.data.local.entity.UserEntity

@Database(
    entities = [
        NodeEntity::class,
        MessageEntity::class,
        UserEntity::class,
        IncidentEntity::class,
        DomainEventEntity::class,
        PeerNameEntity::class,
        IncidentOfferEntity::class,
        com.example.testresqmesh.data.local.entity.SosAlertEntity::class,
        com.example.testresqmesh.data.local.entity.SosEventEntity::class,
        com.example.testresqmesh.data.local.entity.ConversationStateEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun peerNameDao(): PeerNameDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun incidentDao(): IncidentDao
    abstract fun incidentOfferDao(): IncidentOfferDao
    abstract fun domainEventDao(): DomainEventDao
    abstract fun sosDao(): com.example.testresqmesh.data.local.dao.SosDao
    abstract fun conversationStateDao(): com.example.testresqmesh.data.local.dao.ConversationStateDao

    companion object {
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationKind TEXT NOT NULL DEFAULT 'COMMUNITY'")
                db.execSQL("ALTER TABLE messages ADD COLUMN channelId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN sosId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN senderNodeId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE messages SET conversationKind = CASE WHEN targetName IS NOT NULL THEN 'PRIVATE' WHEN isSOS = 1 THEN 'LEGACY_SOS' WHEN audioBase64 IS NOT NULL THEN 'LEGACY_RADIO' ELSE 'COMMUNITY' END")
                db.execSQL("CREATE TABLE IF NOT EXISTS sos_alerts (sosId TEXT NOT NULL PRIMARY KEY, originNodeId TEXT NOT NULL, originName TEXT NOT NULL, signingKey TEXT NOT NULL, emergencyType TEXT NOT NULL, revision INTEGER NOT NULL, ended INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, latitude REAL, longitude REAL, accuracyMeters REAL, locationCapturedAt INTEGER, locallySilenced INTEGER NOT NULL, eventJson TEXT NOT NULL, transmission TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sos_events (eventId TEXT NOT NULL PRIMARY KEY, sosId TEXT NOT NULL, revision INTEGER NOT NULL, eventJson TEXT NOT NULL, pending INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS conversation_state (conversationId TEXT NOT NULL PRIMARY KEY, draft TEXT NOT NULL, lastReadAt INTEGER NOT NULL)")
            }
        }
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `peer_names` (`nodeId` TEXT NOT NULL, `fullName` TEXT NOT NULL, PRIMARY KEY(`nodeId`))")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN workflowVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE incidents ADD COLUMN reporterSigningKey TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN selectionId TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN selectionOfferId TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN selectionOfferRevision INTEGER")
                db.execSQL("ALTER TABLE incidents ADD COLUMN selectedHelperKey TEXT")
                db.execSQL("ALTER TABLE incidents ADD COLUMN selectionConfirmedAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS incident_offers (offerId TEXT NOT NULL PRIMARY KEY, incidentId TEXT NOT NULL, helperKey TEXT NOT NULL, helperNodeId TEXT NOT NULL, helperName TEXT NOT NULL, note TEXT NOT NULL, revision INTEGER NOT NULL, withdrawn INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_incident_offers_incidentId_helperKey ON incident_offers (incidentId, helperKey)")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE domain_events ADD COLUMN validationStatus TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("UPDATE domain_events SET validationStatus = 'ACCEPTED' WHERE applied = 1")
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
                val instance = buildDatabase(context, "resqmesh_database")
                INSTANCE = instance
                instance
            }
        }

        internal fun buildDatabase(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
    }
}
