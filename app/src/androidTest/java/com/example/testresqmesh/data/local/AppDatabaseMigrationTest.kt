package com.example.testresqmesh.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.testresqmesh.data.local.entity.PeerNameEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test fun versionSixToEightKeepsMessagesAndLegacyIncidents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration_6_7_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL("CREATE TABLE nodes (macAddress TEXT NOT NULL, nodeName TEXT NOT NULL, publicKey TEXT, lastSeenTimestamp INTEGER NOT NULL, isBlocked INTEGER NOT NULL, PRIMARY KEY(macAddress))")
            legacy.execSQL("CREATE TABLE messages (msgId TEXT NOT NULL, senderName TEXT NOT NULL, targetName TEXT, text TEXT, imageBase64 TEXT, audioBase64 TEXT, locationLat REAL, locationLng REAL, timestamp INTEGER NOT NULL, isSOS INTEGER NOT NULL, isMine INTEGER NOT NULL, deliveredTo TEXT NOT NULL, seenBy TEXT NOT NULL, outboundRoute TEXT NOT NULL, PRIMARY KEY(msgId))")
            legacy.execSQL("CREATE TABLE users (userId TEXT NOT NULL, deviceId TEXT NOT NULL, displayName TEXT NOT NULL, publicKey TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(userId))")
            legacy.execSQL("CREATE TABLE incidents (incidentId TEXT NOT NULL, creatorId TEXT NOT NULL, creatorName TEXT NOT NULL, incidentType TEXT NOT NULL, severity TEXT NOT NULL, description TEXT NOT NULL, areaDescription TEXT NOT NULL, latitude REAL, longitude REAL, locationCapturedAt INTEGER, locationAccuracyMeters REAL, status TEXT NOT NULL, primaryResponderId TEXT, primaryResponderName TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(incidentId))")
            legacy.execSQL("CREATE TABLE domain_events (eventId TEXT NOT NULL, entityId TEXT NOT NULL, entityType TEXT NOT NULL, eventType TEXT NOT NULL, actorId TEXT NOT NULL, actorName TEXT NOT NULL, logicalVersion INTEGER NOT NULL, timestamp INTEGER NOT NULL, payloadJson TEXT NOT NULL, signature TEXT, applied INTEGER NOT NULL, PRIMARY KEY(eventId))")
            legacy.execSQL("INSERT INTO messages VALUES ('msg', 'sender#1234', 'recipient#5678', 'hello', NULL, NULL, NULL, NULL, 1, 0, 1, 'PENDING', '', '')")
            legacy.execSQL("INSERT INTO incidents VALUES ('incident', 'creator', 'Creator', 'Medical', 'Critical', 'description', 'area', NULL, NULL, NULL, NULL, 'RESOLVED', NULL, NULL, 2, 1, 2)")
            legacy.version = 6
        } finally {
            legacy.close()
        }
        try {
            val upgraded = AppDatabase.buildDatabase(context, name)
            try {
                assertEquals("PENDING", upgraded.messageDao().getMessageById("msg")?.deliveredTo)
                val migratedIncident = upgraded.incidentDao().getIncidentById("incident")
                assertEquals("RESOLVED", migratedIncident?.status)
                assertEquals(1, migratedIncident?.workflowVersion)
                assertEquals("", migratedIncident?.title)
                assertEquals(emptyList<Any>(), upgraded.incidentOfferDao().getForIncident("incident"))
                upgraded.peerNameDao().upsert(PeerNameEntity("5678", "Recipient"))
                assertEquals("Recipient", upgraded.peerNameDao().observeNames().first().single().fullName)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
