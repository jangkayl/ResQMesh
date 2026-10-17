package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextSanitizer
import android.content.Context
import android.content.SharedPreferences
import edu.cit.resqmesh.core.network.CryptoManager
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.data.local.dao.UserDao
import edu.cit.resqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

interface IdentityProvider {
    suspend fun getOrCreateUser(displayName: String? = null): UserEntity
    fun observeUser(): Flow<UserEntity?>
    suspend fun getUserId(): String
    fun getDeviceId(): String
}

class LocalIdentityManager internal constructor(
    private val userDao: UserDao,
    private val prefs: SharedPreferences,
    private val nodeIdProvider: () -> String,
    private val publicKeyProvider: () -> String?,
    private val defaultName: String
) : IdentityProvider {
    constructor(context: Context, userDao: UserDao) : this(userDao,
        context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE),
        { CryptoManager.getMyNodeId() }, { kotlin.runCatching { CryptoManager.getMyPublicKeyBase64() }.getOrNull() },
        android.os.Build.MODEL)

    private val mutex = Mutex()

    override suspend fun getOrCreateUser(displayName: String?): UserEntity = mutex.withLock {
        val cleanName = displayName
            ?.let { TextSanitizer.sanitize(it, TextField.DISPLAY_NAME) }
            ?.takeIf { it.isNotBlank() }
        require(cleanName == null || cleanName.length <= TextField.DISPLAY_NAME.maxLength) {
            "Display name cannot exceed ${TextField.DISPLAY_NAME.maxLength} characters"
        }
        val existing = userDao.getLocalUser()
        if (existing != null) {
            val user = if (cleanName != null && existing.displayName != cleanName) {
                check(userDao.updateDisplayName(existing.userId, cleanName) == 1) { "Local identity could not be updated" }
                AppLogger.d("IDENTITY", "NAME_UPDATED userIdUnchanged=true deviceIdUnchanged=true storedKeyUnchanged=true")
                existing.copy(displayName = cleanName)
            } else existing
            syncPreferences(user)
            return@withLock user
        }

        val (deviceId, publicKey) = withContext(Dispatchers.Default) { nodeIdProvider() to publicKeyProvider() }
        val name = cleanName
            ?: prefs.getString("custom_name", null)
            ?: defaultName

        val savedUserId = prefs.getString("user_id", null)
        val userId = savedUserId ?: "USR-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"

        val newUser = UserEntity(
            userId = userId,
            deviceId = deviceId,
            displayName = name,
            publicKey = publicKey,
            createdAt = System.currentTimeMillis()
        )

        userDao.insertUser(newUser)
        syncPreferences(newUser)
        newUser
    }

    private fun syncPreferences(user: UserEntity) {
        prefs.edit().putString("user_id", user.userId).putString("node_id", user.deviceId)
            .putString("custom_name", user.displayName).apply()
    }

    override fun observeUser(): Flow<UserEntity?> = userDao.observeLocalUser()

    override suspend fun getUserId(): String {
        return getOrCreateUser().userId
    }

    override fun getDeviceId(): String {
        return nodeIdProvider()
    }
}
