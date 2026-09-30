package com.example.testresqmesh.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.entity.IncidentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentSyncDaoTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() = database.close()

    @Test fun resolvedAndCancelledIncidentsRemainInReconnectSummary() = runBlocking {
        database.incidentDao().insertOrUpdate(incident("resolved", "RESOLVED", 3L))
        database.incidentDao().insertOrUpdate(incident("cancelled", "CANCELLED", 4L))
        assertEquals(
            listOf("cancelled", "resolved"),
            database.incidentDao().getRecentIncidentsForSync(24).map { it.incidentId }
        )
    }

    private fun incident(id: String, status: String, updatedAt: Long) = IncidentEntity(
        incidentId = id,
        creatorId = "creator",
        creatorName = "Creator",
        incidentType = "Medical",
        severity = "Critical",
        description = "test",
        areaDescription = "area",
        status = status,
        primaryResponderId = null,
        primaryResponderName = null,
        version = 2L,
        createdAt = 1L,
        updatedAt = updatedAt
    )
}
