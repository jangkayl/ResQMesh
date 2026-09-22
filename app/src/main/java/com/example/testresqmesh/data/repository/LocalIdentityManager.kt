package com.example.testresqmesh.data.repository

import android.content.Context
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.data.local.dao.UserDao
import com.example.testresqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface IdentityProvider {
    suspend fun getOrCreateUser(displayName: String? = null): UserEntity
    fun observeUser(): Flow<UserEntity?>
    suspend fun getUserId(): String
    fun getDeviceId(): String
}

class LocalIdentityManager(
    private val context: Context,
    private val userDao: UserDao
) : IdentityProvider {
    private val prefs = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)

    override suspend fun getOrCreateUser(displayName: String?): UserEntity {
        val existing = userDao.getLocalUser()
        if (existing != null) {
            if (displayName != null && displayName.isNotBlank() && existing.displayName != displayName) {
                userDao.updateDisplayName(existing.userId, displayName)
                prefs.edit().putString("custom_name", displayName).apply()
                return existing.copy(displayName = displayName)
            }
            return existing
        }

        val deviceId = CryptoManager.getMyNodeId()
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: prefs.getString("custom_name", null)
            ?: android.os.Build.MODEL

        val savedUserId = prefs.getString("user_id", null)
        val userId = savedUserId ?: "USR-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val publicKey = try {
            CryptoManager.getMyPublicKeyBase64()
        } catch (e: Exception) {
            null
        }

        val newUser = UserEntity(
            userId = userId,
            deviceId = deviceId,
            displayName = name,
            publicKey = publicKey,
            createdAt = System.currentTimeMillis()
        )

        userDao.insertUser(newUser)
        prefs.edit()
            .putString("user_id", userId)
            .putString("node_id", deviceId)
            .putString("custom_name", name)
            .apply()

        return newUser
    }

    override fun observeUser(): Flow<UserEntity?> = userDao.observeLocalUser()

    override suspend fun getUserId(): String {
        return prefs.getString("user_id", null) ?: getOrCreateUser().userId
    }

    override fun getDeviceId(): String {
        return CryptoManager.getMyNodeId()
    }
}
