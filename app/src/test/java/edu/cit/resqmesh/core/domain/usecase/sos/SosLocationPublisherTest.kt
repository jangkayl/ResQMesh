package edu.cit.resqmesh.core.domain.usecase.sos

import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.core.model.SosLocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

class SosLocationPublisherTest {
    private val created = 1_000_000L
    private val published = created + 1_000L

    // About 111 m per 0.001 degree of latitude.
    private val moved = 14.501
    private val inPlace = 14.50001

    private fun alert(
        lat: Double? = 14.5,
        accuracy: Float? = 8f,
        capturedAt: Long? = published,
        ended: Boolean = false
    ) = SosAlert("sos", "node", "name", "key", "Medical", 2, ended, created, published,
        lat, lat?.let { 121.0 }, accuracy, capturedAt, false, "{}", "SENT")

    private fun fix(lat: Double, accuracy: Float = 8f, age: Long = 61_000L) =
        SosLocationSample(lat, 121.0, accuracy, published + age)

    @Test
    fun stationaryPersonPublishesNothing() {
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(alert(), fix(inPlace, age = 3_600_000L)))
    }

    @Test
    fun movementPublishesAndMayBeLessAccurate() {
        assertEquals(SosLocationDecision.PUBLISH_MOVED, SosLocationPublisher.decide(alert(), fix(moved, 30f)))
    }

    @Test
    fun movementInsideTheFixErrorIsNotMovement() {
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(alert(), fix(moved, 200f)))
    }

    @Test
    fun movementWithinTheMinimumGapWaits() {
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(alert(), fix(moved, age = 59_000L)))
    }

    @Test
    fun olderSosPublishesLessOften() {
        val oldPublish = created + SOS_LOCATION_RELAX_AFTER_MS
        val old = alert(capturedAt = oldPublish)
        val soon = SosLocationSample(moved, 121.0, 8f, oldPublish + SOS_LOCATION_MIN_GAP_MS)
        val later = SosLocationSample(moved, 121.0, 8f, oldPublish + SOS_LOCATION_RELAXED_GAP_MS)
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(old, soon))
        assertEquals(SosLocationDecision.PUBLISH_MOVED, SosLocationPublisher.decide(old, later))
    }

    @Test
    fun halvedErrorPublishesWithoutMovement() {
        val rough = alert(accuracy = 100f)
        assertEquals(SosLocationDecision.PUBLISH, SosLocationPublisher.decide(rough, fix(inPlace, 50f)))
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(rough, fix(inPlace, 51f)))
    }

    @Test
    fun firstCoordinatesPublishAtOnce() {
        val soon = fix(inPlace, age = 1L)
        assertEquals(SosLocationDecision.PUBLISH, SosLocationPublisher.decide(alert(lat = null), soon))
        assertEquals(SosLocationDecision.PUBLISH, SosLocationPublisher.decide(alert(capturedAt = null), soon))
    }

    @Test
    fun endedSosPublishesNothing() {
        assertEquals(SosLocationDecision.SKIP, SosLocationPublisher.decide(alert(ended = true), fix(moved)))
    }
}
