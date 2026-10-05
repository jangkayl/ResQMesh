package com.example.testresqmesh.data.repository.incident

import com.example.testresqmesh.data.local.entity.DomainEventEntity
import org.json.JSONObject


internal fun eventToJson(event: DomainEventEntity): JSONObject = JSONObject().apply {
        put("eventId", event.eventId)
        put("entityId", event.entityId)
        put("entityType", event.entityType)
        put("eventType", event.eventType)
        put("actorId", event.actorId)
        put("actorName", event.actorName)
        put("logicalVersion", event.logicalVersion)
        put("timestamp", event.timestamp)
        put("payloadJson", event.payloadJson)
        event.signature?.let { put("signature", it) }
    }

internal fun JSONObject.optionalDouble(name: String): Double? =
        if (has(name) && !isNull(name)) getDouble(name) else null

internal fun JSONObject.optionalLong(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

internal fun JSONObject.optionalFloat(name: String): Float? =
        if (has(name) && !isNull(name)) getDouble(name).toFloat() else null
