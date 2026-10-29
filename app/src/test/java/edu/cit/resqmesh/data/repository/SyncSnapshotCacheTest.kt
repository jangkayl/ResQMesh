package edu.cit.resqmesh.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SyncSnapshotCacheTest {
    private val scope = IncidentSyncScope(1_000, 100)
    private fun snapshot() = IncidentSyncSnapshot(emptyList(), emptyList(), emptyList())

    @Test fun repeatRequestsReuseTheSnapshotUntilAWrite() = runTest {
        val cache = SyncSnapshotCache()
        var reads = 0
        val first = cache.get(scope) { reads++; snapshot() }
        assertSame(first, cache.get(scope) { reads++; snapshot() })
        assertEquals(1, reads)
        cache.invalidate()
        cache.get(scope) { reads++; snapshot() }
        assertEquals(2, reads)
    }

    @Test fun otherScopesAreComputedSeparately() = runTest {
        val cache = SyncSnapshotCache()
        var reads = 0
        cache.get(scope) { reads++; snapshot() }
        cache.get(scope.copy(since = 2_000)) { reads++; snapshot() }
        assertEquals(2, reads)
    }

    @Test fun aSnapshotRacedByAWriteIsReturnedButNotKept() = runTest {
        val cache = SyncSnapshotCache()
        var reads = 0
        cache.get(scope) { reads++; cache.invalidate(); snapshot() }
        cache.get(scope) { reads++; snapshot() }
        assertEquals(2, reads)
    }
}
