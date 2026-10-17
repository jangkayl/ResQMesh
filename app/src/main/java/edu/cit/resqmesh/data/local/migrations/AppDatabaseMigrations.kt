package edu.cit.resqmesh.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

/**
 * Versions 2 and 3 briefly stored private-image metadata. This is intentionally a
 * forward migration: rebuilding messages removes only the retired attachmentId column
 * while retaining every chat row, then drops the retired attachment tables.
*/
internal val MIGRATION_2_4 = object : Migration(2, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = removeLegacyAttachmentSchema(db)
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = removeLegacyAttachmentSchema(db)
}

/** Version 1 already has the current schema; this only advances its schema identity. */
internal val MIGRATION_1_4 = object : Migration(1, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`userId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `publicKey` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `incidents` (`incidentId` TEXT NOT NULL, `creatorId` TEXT NOT NULL, `creatorName` TEXT NOT NULL, `incidentType` TEXT NOT NULL, `severity` TEXT NOT NULL, `description` TEXT NOT NULL, `areaDescription` TEXT NOT NULL, `status` TEXT NOT NULL, `primaryResponderId` TEXT, `primaryResponderName` TEXT, `version` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`incidentId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `domain_events` (`eventId` TEXT NOT NULL, `entityId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `eventType` TEXT NOT NULL, `actorId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `logicalVersion` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, `signature` TEXT, `applied` INTEGER NOT NULL, PRIMARY KEY(`eventId`))")
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE incidents ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE incidents ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE incidents ADD COLUMN locationCapturedAt INTEGER")
        db.execSQL("ALTER TABLE incidents ADD COLUMN locationAccuracyMeters REAL")
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `peer_names` (`nodeId` TEXT NOT NULL, `fullName` TEXT NOT NULL, PRIMARY KEY(`nodeId`))")
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
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
