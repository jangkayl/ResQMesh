package com.example.testresqmesh.data.repository

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerPublicKeyDirectoryTest {

    private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for ((k, v) in temp) {
                if (v == null) data.remove(k) else data[k] = v
            }
            temp.clear()
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = null
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun observeTrustsOnFirstUse() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        val result = dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        assertEquals(PeerPublicKeyDirectory.Observation.TRUSTED, result)
        assertEquals("KEY_1", dir.trustedKey("Bob [NODE]#B001"))
        assertFalse(dir.hasPendingChange("Bob [NODE]#B001"))
    }

    @Test
    fun observeSameKeyReturnsUnchanged() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        val result = dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        assertEquals(PeerPublicKeyDirectory.Observation.UNCHANGED, result)
        assertFalse(dir.hasPendingChange("Bob [NODE]#B001"))
    }

    @Test
    fun observeDifferentKeyReturnsPending() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        val result = dir.observe("Bob [NODE]#B001", "B001", "KEY_2")
        assertEquals(PeerPublicKeyDirectory.Observation.KEY_CHANGE_PENDING, result)
        assertTrue(dir.hasPendingChange("Bob [NODE]#B001"))
        // Trusted key remains original until accepted
        assertEquals("KEY_1", dir.trustedKey("Bob [NODE]#B001"))
    }

    @Test
    fun fallbackLookupByDisplayName() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        // Lookup using display name without '#B001'
        assertEquals("KEY_1", dir.trustedKey("Bob"))
        assertFalse(dir.hasPendingChange("Bob"))
    }

    @Test
    fun acceptPendingChangeUpdatesKey() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        dir.observe("Bob [NODE]#B001", "B001", "KEY_2")
        assertTrue(dir.hasPendingChange("Bob [NODE]#B001"))

        // Accept using display name fallback
        val accepted = dir.acceptPendingChange("Bob")
        assertTrue(accepted)
        assertFalse(dir.hasPendingChange("Bob [NODE]#B001"))
        assertEquals("KEY_2", dir.trustedKey("Bob [NODE]#B001"))
    }

    @Test
    fun rejectPendingChangeClearsPendingAndRetainsOriginalKey() {
        val prefs = FakeSharedPreferences()
        val dir = PeerPublicKeyDirectory(prefs)

        dir.observe("Bob [NODE]#B001", "B001", "KEY_1")
        dir.observe("Bob [NODE]#B001", "B001", "KEY_2")
        assertTrue(dir.hasPendingChange("Bob [NODE]#B001"))

        // Reject using full identity
        val rejected = dir.rejectPendingChange("Bob [NODE]#B001")
        assertTrue(rejected)
        assertFalse(dir.hasPendingChange("Bob [NODE]#B001"))
        assertEquals("KEY_1", dir.trustedKey("Bob [NODE]#B001"))
    }
}
