package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.data.local.entity.EventValidationStatus
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

enum class IncidentIngestionResult { APPLIED, DEFERRED, DUPLICATE, REJECTED }

/**
 * Which incidents one sync session compares: every open incident plus closures updated at or after
 * [since], open first and newest next, at most [cap]. The initiator chooses it and sends it with every
 * request, so both phones hash exactly the same set whatever their clocks say.
 */
data class IncidentSyncScope(val since: Long, val cap: Int) {
    fun writeTo(json: JSONObject): JSONObject = json.put("since", since).put("cap", cap)

    companion object {
        const val CLOSED_WINDOW_MS = 24L * 60 * 60 * 1000
        const val MAX_INCIDENTS = 100
        private const val MAX_SKEW_MS = 12L * 60 * 60 * 1000
        private const val MINUTE_MS = 60_000L

        /** Rounded to the minute so repeated sessions share a cached snapshot. */
        fun current(now: Long = System.currentTimeMillis()) =
            IncidentSyncScope((now - CLOSED_WINDOW_MS) / MINUTE_MS * MINUTE_MS, MAX_INCIDENTS)

        /**
         * A peer's requested scope, or null unless it is the standard window: the fixed cap and a
         * minute-aligned `since` within 12 h of ours. That bounds how many distinct snapshots a peer can
         * force past the cache while tolerating ordinary clock skew.
         */
        fun from(json: JSONObject, now: Long = System.currentTimeMillis()): IncidentSyncScope? {
            val since = json.optLong("since", -1)
            val standard = json.optInt("cap") == MAX_INCIDENTS && since > 0 && since % MINUTE_MS == 0L &&
                kotlin.math.abs(since - (now - CLOSED_WINDOW_MS)) <= MAX_SKEW_MS
            return if (standard) IncidentSyncScope(since, MAX_INCIDENTS) else null
        }
    }
}

/**
 * Reuses the last scoped snapshot until a tracked table changes. [invalidate] runs after every write
 * (Room's invalidation tracker), and a snapshot computed while a write raced it is returned but not kept.
 */
class SyncSnapshotCache {
    private val version = AtomicLong()
    @Volatile private var cached: Triple<IncidentSyncScope, Long, IncidentSyncSnapshot>? = null

    fun invalidate() { version.incrementAndGet() }

    suspend fun get(scope: IncidentSyncScope, compute: suspend () -> IncidentSyncSnapshot): IncidentSyncSnapshot {
        val before = version.get()
        cached?.let { (cachedScope, cachedVersion, snapshot) ->
            if (cachedScope == scope && cachedVersion == before) return snapshot
        }
        val fresh = compute()
        if (version.get() == before) cached = Triple(scope, before, fresh)
        return fresh
    }
}

/** No local delivery/reachability fields participate in these hashes. */
data class IncidentSyncEntry(val id: String, val history: String, val state: String) {
    fun json(): JSONObject = JSONObject().put("id", id).put("history", history).put("state", state)
}

class IncidentSyncSnapshot(
    history: List<DomainEventEntity>,
    incidents: List<IncidentEntity>,
    offers: List<IncidentOfferEntity>,
) {
    val events = history.filter { it.entityType == "INCIDENT" && it.validationStatus == EventValidationStatus.ACCEPTED }
        .sortedBy { it.eventId }
    private val incidentMap = incidents.associateBy { it.incidentId }
    // Orphans (events whose incident is unknown) never count: one unresolvable event must not block COMPLETE.
    val pending = history.count {
        it.entityType == "INCIDENT" && !it.applied && it.validationStatus != EventValidationStatus.REJECTED && it.entityId in incidentMap
    }
    private val eventGroups = events.groupBy { it.entityId }
    private val offerGroups = offers.groupBy { it.incidentId }
    val entries: List<IncidentSyncEntry> = (incidents.map { it.incidentId } + events.map { it.entityId })
        .distinct().sorted().map { id ->
            val incident = incidentMap[id]
            IncidentSyncEntry(id,
                hash(eventGroups[id].orEmpty().flatMap { listOf(it.eventId, eventHash(it)) }),
                hash(listOf(incident?.let(::incidentFields).orEmpty()) + offerGroups[id].orEmpty()
                    .sortedBy { it.offerId }.map(::offerFields)))
        }
    val historyRoot = hash(entries.flatMap { listOf(it.id, it.history) })
    val stateRoot = hash(entries.flatMap { listOf(it.id, it.state) })
    // The token also changes when validation/application changes without changing history bytes.
    val token = hash(listOf(historyRoot, stateRoot, pending.toString()) + history.sortedBy { it.eventId }
        .map { "${it.eventId}:${it.validationStatus}:${it.applied}" })

    fun digest(): JSONObject = JSONObject().put("history", historyRoot).put("state", stateRoot)
        .put("snapshot", token).put("pending", pending)

    fun matches(digest: JSONObject): Boolean = pending == 0 && digest.optInt("pending", -1) == 0 &&
        historyRoot == digest.optString("history") && stateRoot == digest.optString("state")

    companion object {
        fun eventHash(e: DomainEventEntity): String = hash(listOf(e.eventId, e.entityId, e.entityType,
            e.eventType, e.actorId, e.actorName, e.logicalVersion.toString(), e.timestamp.toString(), e.payloadJson))

        /** Length prefixes prevent ambiguous concatenation; order is defined by callers. */
        fun hash(fields: List<String>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out -> fields.forEach {
                val value = it.toByteArray(Charsets.UTF_8)
                out.writeInt(value.size)
                out.write(value)
            } }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

        private fun incidentFields(i: IncidentEntity): String = hash(listOf(
            i.incidentId, i.creatorId, i.creatorName, i.incidentType, i.severity, i.description,
            i.areaDescription, i.title, i.latitude?.toString(), i.longitude?.toString(),
            i.locationCapturedAt?.toString(), i.locationAccuracyMeters?.toString(), i.status,
            i.primaryResponderId, i.primaryResponderName, i.version.toString(), i.createdAt.toString(),
            i.updatedAt.toString(), i.workflowVersion.toString(), i.reporterSigningKey, i.selectionId,
            i.selectionOfferId, i.selectionOfferRevision?.toString(), i.selectedHelperKey,
            i.selectionConfirmedAt?.toString()
        ).map { if (it == null) "0" else "1$it" })

        private fun offerFields(o: IncidentOfferEntity): String = hash(listOf(o.offerId, o.incidentId,
            o.helperKey, o.helperNodeId, o.helperName, o.note, o.revision.toString(), o.withdrawn.toString(), o.updatedAt.toString()))

        fun eventJson(e: DomainEventEntity): JSONObject = JSONObject().apply {
            put("eventId", e.eventId); put("entityId", e.entityId); put("entityType", e.entityType)
            put("eventType", e.eventType); put("actorId", e.actorId); put("actorName", e.actorName)
            put("logicalVersion", e.logicalVersion); put("timestamp", e.timestamp); put("payloadJson", e.payloadJson)
            e.signature?.let { put("signature", it) }
        }
    }
}

interface IncidentSyncStore {
    suspend fun snapshot(scope: IncidentSyncScope): IncidentSyncSnapshot
    suspend fun ingest(json: JSONObject): IncidentIngestionResult
    suspend fun replay(incidentId: String)
    suspend fun rebuild(incidentId: String)
}
