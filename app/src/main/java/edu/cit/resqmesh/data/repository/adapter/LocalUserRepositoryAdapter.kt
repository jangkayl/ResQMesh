package edu.cit.resqmesh.data.repository.adapter

import edu.cit.resqmesh.core.domain.repository.LocalUserRepository
import edu.cit.resqmesh.core.model.MeshUser
import edu.cit.resqmesh.data.mapper.toModel
import edu.cit.resqmesh.data.repository.IdentityProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalUserRepositoryAdapter(private val identity: IdentityProvider) : LocalUserRepository {
    override suspend fun getOrCreateUser(displayName: String?): MeshUser =
        identity.getOrCreateUser(displayName).toModel()
    override fun observeUser(): Flow<MeshUser?> = identity.observeUser().map { it?.toModel() }
    override suspend fun getUserId(): String = identity.getUserId()
    override fun getDeviceId(): String = identity.getDeviceId()
}
