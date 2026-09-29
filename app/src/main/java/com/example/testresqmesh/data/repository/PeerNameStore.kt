package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.data.local.dao.PeerNameDao
import com.example.testresqmesh.data.local.entity.PeerNameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PeerNameStore {
    val names: Flow<Map<String, String>>
    suspend fun observeFullName(fullName: String)
}

class RoomPeerNameStore(private val dao: PeerNameDao) : PeerNameStore {
    override val names: Flow<Map<String, String>> = dao.observeNames().map { entries ->
        entries.associate { it.nodeId to it.fullName }
    }

    override suspend fun observeFullName(fullName: String) {
        val nodeId = NodeIdentity.idOf(fullName) ?: return
        if (NodeIdentity.isPlaceholder(fullName) || fullName.substringBeforeLast('#').isBlank()) return
        dao.upsert(PeerNameEntity(nodeId, fullName.trim()))
    }
}
