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
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test fun versionTenToElevenSeparatesHistoryAndPersistsScopedDraftsAndTerminalSos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val name = "migration_10_11_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = JSONObject(assets.open("com.example.testresqmesh.data.local.AppDatabase/10.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indexes = e.getJSONArray("indices")
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            old.execSQL("INSERT INTO messages VALUES ('community', 'Peer', NULL, 'hello', NULL, NULL, NULL, NULL, 1, 0, 0, '', '', '')")
            old.execSQL("INSERT INTO messages VALUES ('radio', 'Peer', NULL, 'Voice message', NULL, 'audio', NULL, NULL, 2, 0, 0, '', '', '')")
            old.execSQL("INSERT INTO messages VALUES ('sos', 'Peer', NULL, 'old SOS', NULL, NULL, NULL, NULL, 3, 1, 0, '', '', '')")
            old.execSQL("INSERT INTO messages VALUES ('private', 'Peer', 'Me', 'private', NULL, 'audio', NULL, NULL, 4, 0, 0, '', '', '')")
            old.version = 10
        }
        try {
            var db = AppDatabase.buildDatabase(context, name)
            val dao = db.messageDao()
            assertEquals("COMMUNITY", dao.getMessageById("community")?.conversationKind)
            assertEquals("LEGACY_RADIO", dao.getMessageById("radio")?.conversationKind)
            assertEquals("", dao.getMessageById("radio")?.channelId)
            assertEquals("LEGACY_SOS", dao.getMessageById("sos")?.conversationKind)
            assertEquals("PRIVATE", dao.getMessageById("private")?.conversationKind)
            assertEquals(listOf("community"), dao.getPublicMessages().first().map { it.msgId })
            assertEquals(4, dao.getAllMessagesOnce().size)
            assertEquals(emptyList<Any>(), db.sosDao().alerts())
            val states = db.conversationStateDao()
            states.draft("RADIO:1", "channel one")
            states.draft("RADIO:2", "channel two")
            states.read("RADIO:1", 100)
            states.draft("SOS:alert", "help")
            val alert = com.example.testresqmesh.data.local.entity.SosAlertEntity("alert", "A", "A", "key", "Medical",
                2, true, 1, 2, null, null, null, null, true, "{}", "QUEUED")
            db.sosDao().putAlert(alert)
            db.close()
            db = AppDatabase.buildDatabase(context, name)
            try {
                assertEquals(alert, db.sosDao().alert("alert"))
                val restored = db.conversationStateDao().observe().first().associateBy { it.conversationId }
                assertEquals("channel one", restored["RADIO:1"]?.draft)
                assertEquals(100L, restored["RADIO:1"]?.lastReadAt)
                assertEquals("channel two", restored["RADIO:2"]?.draft)
                assertEquals(0L, restored["RADIO:2"]?.lastReadAt)
                assertEquals("help", restored["SOS:alert"]?.draft)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionNineToTenSeparatesValidationFromApplicationWithoutLosingHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val name = "migration_9_10_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = JSONObject(testContext.assets.open("com.example.testresqmesh.data.local.AppDatabase/9.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.getJSONArray("indices")
                for (j in 0 until indexes.length())
                    old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO domain_events VALUES ('accepted', 'i', 'INCIDENT', 'INCIDENT_CREATED', 'a', 'A', 1, 1, '{}', NULL, 1)")
            old.execSQL("INSERT INTO domain_events VALUES ('pending', 'i', 'INCIDENT', 'INCIDENT_CANCELLED', 'a', 'A', 2, 2, '{}', NULL, 0)")
            old.version = 9
        }
        try {
            val upgraded = AppDatabase.buildDatabase(context, name)
            try {
                val events = upgraded.domainEventDao()
                assertEquals("ACCEPTED", events.getEventById("accepted")?.validationStatus)
                assertEquals(true, events.getEventById("accepted")?.applied)
                assertEquals("UNVERIFIED", events.getEventById("pending")?.validationStatus)
                assertEquals(false, events.getEventById("pending")?.applied)
                assertEquals(2, events.getIncidentHistory().size)
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
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
