package com.example.testresqmesh.data.repository

import android.content.SharedPreferences
import com.example.testresqmesh.data.local.dao.UserDao
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.feature.incident.viewmodel.incidentPresentation
import com.example.testresqmesh.feature.incident.viewmodel.isInvolved
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class LocalIdentityManagerTest {
    private val dao = Users()
    private val values = mutableMapOf<String, String>()
    private val prefs = preferences(values)
    private fun manager() = LocalIdentityManager(dao, prefs, { "node" }, { "public-key" }, "Phone")

    @Test fun concurrentInitializationAndRenameCreateOneStableIdentity() = runTest {
        val identity = manager()
        val users = (1..20).map { index -> async { identity.getOrCreateUser("Name $index") } }.awaitAll()
        assertEquals(1, dao.inserts)
        assertEquals(1, users.map { it.userId }.distinct().size)
        assertEquals(1, users.map { it.deviceId }.distinct().size)
        assertEquals(dao.user.value!!.userId, values["user_id"])
        assertEquals(dao.user.value!!.displayName, values["custom_name"])
    }

    @Test fun roomWinsOverStalePreferencesAndRestartKeepsIdentity() = runTest {
        val identity = manager()
        val original = identity.getOrCreateUser("Kyle")
        values["user_id"] = "stale-other-id"
        values["custom_name"] = "stale-name"
        assertEquals(original.userId, identity.getUserId())
        assertEquals(original.displayName, values["custom_name"])
        val renamed = identity.getOrCreateUser("  New name  ")
        assertEquals(original.copy(displayName = "New name"), renamed)
        assertEquals(renamed, manager().getOrCreateUser())
        assertEquals(1, dao.inserts)
    }

    @Test fun renameKeepsReporterAuthorityAtEveryStageWithoutRewritingReportName() = runTest {
        val identity = manager()
        val original = identity.getOrCreateUser("Kyle")
        val report = IncidentEntity("incident", original.userId, "Kyle", "Medical", "Serious", "Need help", "School",
            status = "OPEN", primaryResponderId = null, primaryResponderName = null, version = 1,
            createdAt = 1, updatedAt = 1, workflowVersion = 2, reporterSigningKey = "signing-key")
        val renamed = identity.getOrCreateUser("New name")
        listOf("OPEN", "AWAITING_HELPER", "RESPONDING").forEach { status ->
            val incident = report.copy(status = status)
            assertTrue(IncidentOwnership.isReporter(incident, renamed.userId, "signing-key"))
            assertTrue(isInvolved(incident, emptyList(), renamed.userId, "signing-key"))
            assertTrue(incidentPresentation(incident, emptyList(), renamed.userId, "signing-key").isReporter)
            assertEquals("Kyle", incident.creatorName)
            assertFalse(IncidentOwnership.isReporter(incident, "another-user", "signing-key"))
            assertFalse(IncidentOwnership.isReporter(incident, renamed.userId, "other-key"))
            assertFalse(IncidentOwnership.isReporter(incident, renamed.userId, null))
        }
    }

    private class Users : UserDao {
        val user = MutableStateFlow<UserEntity?>(null)
        var inserts = 0
        override suspend fun getLocalUser(): UserEntity? { yield(); return user.value }
        override fun observeLocalUser() = user
        override suspend fun getUserById(userId: String) = user.value?.takeIf { it.userId == userId }
        override suspend fun getUserByDeviceId(deviceId: String) = user.value?.takeIf { it.deviceId == deviceId }
        override suspend fun insertUser(user: UserEntity): Long { yield(); this.user.value = user; inserts++; return 1 }
        override suspend fun updateDisplayName(userId: String, displayName: String): Int {
            yield()
            val current = user.value?.takeIf { it.userId == userId } ?: return 0
            user.value = current.copy(displayName = displayName)
            return 1
        }
    }

    private fun preferences(values: MutableMap<String, String>): SharedPreferences {
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1] as String; proxy }
                "apply" -> null
                "commit" -> true
                else -> null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }
}
