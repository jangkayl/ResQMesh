package edu.cit.resqmesh.data.local.entity

import kotlinx.serialization.Serializable

/** One public message a neighbor can offer, without its text or attachments. */
@Serializable
data class ChatSyncEntry(
    val id: String,
    val sentAt: Long,
    val kind: String,
    val senderName: String,
    val sosId: String = "",
    val mediaKind: String = ""
)

/** The text of a public message. Attachments never travel in a sync page; [mediaKind] says one exists. */
@Serializable
data class ChatSyncRow(
    val id: String,
    val sentAt: Long,
    val kind: String,
    val senderName: String,
    val senderNodeId: String,
    val text: String,
    val sosId: String = "",
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val mediaKind: String = ""
)

/** A placeholder still waiting for its attachment. */
data class PendingSyncMedia(val id: String, val kind: String, val sosId: String)

enum class ChatSyncIngestion { STORED, REJECTED, DEFERRED }
