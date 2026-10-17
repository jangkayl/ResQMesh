package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.data.repository.IdentityProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalUserRepositoryAdapterTest {
    private val user = UserEntity("USR-1", "dev-1", "Ada", "pk", 5L)
    private class Fake(val user: UserEntity) : IdentityProvider {
        val seen = mutableListOf<String?>()
        val flow = MutableStateFlow<UserEntity?>(null)
        override suspend fun getOrCreateUser(displayName: String?): UserEntity { seen += displayName; return user }
        override fun observeUser(): Flow<UserEntity?> = flow
        override suspend fun getUserId() = user.userId
        override fun getDeviceId() = user.deviceId
    }

    @Test fun delegatesAndMapsUser() = runBlocking {
        val fake = Fake(user)
        val adapter = LocalUserRepositoryAdapter(fake)
        val model = adapter.getOrCreateUser("Ada")
        assertEquals(listOf<String?>("Ada"), fake.seen)
        assertEquals("USR-1", model.userId)
        assertEquals("pk", model.publicKey)
        assertEquals("USR-1", adapter.getUserId())
        assertEquals("dev-1", adapter.getDeviceId())
        assertNull(adapter.observeUser().first())
        fake.flow.value = user
        assertEquals("Ada", adapter.observeUser().first()?.displayName)
    }
}
