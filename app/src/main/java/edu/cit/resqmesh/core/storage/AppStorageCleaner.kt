package edu.cit.resqmesh.core.storage

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Settings "Storage & data" actions. The cache holds only temporary voice and capture files,
 * so clearing it never touches messages, identity, keys or offline maps. A reset delegates to
 * [ActivityManager.clearApplicationUserData], which wipes all app storage and Keystore keys and
 * kills the process.
 */
class AppStorageCleaner internal constructor(
    private val cacheDir: File,
    private val wipeAppData: () -> Boolean
) {
    constructor(context: Context) : this(
        context.applicationContext.cacheDir,
        { context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true }
    )

    fun cacheBytes(): Long = cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Deletes everything inside the cache directory and keeps the directory. Returns bytes freed. */
    fun clearCache(): Long {
        val before = cacheBytes()
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return (before - cacheBytes()).coerceAtLeast(0)
    }

    /** Returns false if Android refused; on success the process is killed before this returns. */
    fun resetAppData(): Boolean = wipeAppData()
}
