package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.domain.repository.LocalUserRepository
import com.example.testresqmesh.core.model.MeshUser
import com.example.testresqmesh.data.mapper.toModel
import com.example.testresqmesh.data.repository.IdentityProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalUserRepositoryAdapter(private val identity: IdentityProvider) : LocalUserRepository {
    override suspend fun getOrCreateUser(displayName: String?): MeshUser =
        identity.getOrCreateUser(displayName).toModel()
    override fun observeUser(): Flow<MeshUser?> = identity.observeUser().map { it?.toModel() }
    override suspend fun getUserId(): String = identity.getUserId()
    override fun getDeviceId(): String = identity.getDeviceId()
}
