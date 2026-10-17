package com.example.testresqmesh.core.domain.repository

import com.example.testresqmesh.core.model.MeshUser
import kotlinx.coroutines.flow.Flow

/** The local user's identity record. */
interface LocalUserRepository {
    suspend fun getOrCreateUser(displayName: String? = null): MeshUser
    fun observeUser(): Flow<MeshUser?>
    suspend fun getUserId(): String
    fun getDeviceId(): String
}
