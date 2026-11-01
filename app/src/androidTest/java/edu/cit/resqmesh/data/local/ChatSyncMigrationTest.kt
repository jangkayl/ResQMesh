package edu.cit.resqmesh.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.cit.resqmesh.data.local.migrations.MIGRATION_11_12
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatSyncMigrationTest {
    @Test fun versionElevenToTwelveKeepsMessagesAsLiveRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val name = "migration_11_12_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = JSONObject(assets.open("edu.cit.resqmesh.data.local.AppDatabase/11.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indexes = e.getJSONArray("indices")
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql")
                    .replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            old.execSQL("INSERT INTO messages (msgId, senderName, timestamp, isSOS, isMine, deliveredTo, seenBy, " +
                "outboundRoute) VALUES ('kept', 'Ann#A1', 5, 0, 0, '', '', '')")
            old.version = 11
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_11_12).build()
        val row = database.messageDao().getMessageById("kept")!!
        assertEquals(0, row.syncState)
        assertEquals("", row.syncMediaKind)
        database.close()
        context.deleteDatabase(name)
    }
}
