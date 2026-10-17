package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.model.SosAlert
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.data.mapper.toModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowAdaptersTest {
    private fun entity(id: String) = SosAlertEntity(
        sosId = id, originNodeId = "n", originName = "o", signingKey = "k", emergencyType = "FIRE",
        revision = 1L, ended = false, createdAt = 1L, updatedAt = 1L, latitude = null, longitude = null,
        accuracyMeters = null, locationCapturedAt = null, locallySilenced = false, eventJson = "{}",
        transmission = "QUEUED"
    )

    @Test fun mappedStateFlowHoldsCurrentValueBeforeAnyCollection() {
        val scope = TestScope(StandardTestDispatcher())
        val source = MutableStateFlow(listOf(entity("a"), entity("b")))
        val mapped = source.mapState(scope) { list -> list.map { it.toModel() } }
        // No dispatcher turn has run: the first frame must already equal the mapped repository value.
        assertEquals(listOf("a", "b"), mapped.value.map(SosAlert::sosId))
        assertEquals(source.value.map { it.toModel() }, mapped.value)
    }

    @Test fun mappedStateFlowFollowsSourceAndKeepsNull() {
        val scope = TestScope(StandardTestDispatcher())
        val source = MutableStateFlow<SosAlertEntity?>(null)
        val mapped = source.mapState(scope) { it?.toModel() }
        assertNull(mapped.value)
        source.value = entity("x")
        scope.advanceUntilIdle()
        assertEquals("x", mapped.value?.sosId)
        scope.testScheduler.advanceUntilIdle()
    }
}
