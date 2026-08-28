package com.example.testresqmesh.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MeshPayload(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val type: String = "MESSAGE", // MESSAGE, SYSTEM, SEEN, DELIVERED
    @ProtoNumber(3) val senderName: String = "",
    @ProtoNumber(4) val targetName: String = "",
    @ProtoNumber(5) val text: String = "",
    @ProtoNumber(6) val image: String? = null,
    @ProtoNumber(7) val audio: String? = null,
    @ProtoNumber(8) val isPrivate: Boolean = false,
    @ProtoNumber(9) val isEncrypted: Boolean = false,
    @ProtoNumber(10) val encryptedData: String? = null,
    @ProtoNumber(11) val encryptedKey: String? = null,
    @ProtoNumber(12) val locationLat: Double? = null,
    @ProtoNumber(13) val locationLng: Double? = null,
    @ProtoNumber(14) val routePath: List<String> = emptyList(),
    @ProtoNumber(15) val directedRoute: List<String> = emptyList(),
    @ProtoNumber(16) val returnRoute: List<String> = emptyList(),
    @ProtoNumber(17) val isSOS: Boolean = false,
    @ProtoNumber(18) val isSOSCancel: Boolean = false,
    @ProtoNumber(19) val publicKey: String = "",
    @ProtoNumber(20) val connectedNodes: List<String> = emptyList(),
    @ProtoNumber(21) val targetMessageId: String = "",
    @ProtoNumber(22) val reader: String = ""
)
