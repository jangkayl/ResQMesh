package com.example.testresqmesh.core.di

import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-owned work scope for repositories that outlive a screen but must still have an explicit
 * owner. Koin keeps this singleton for the app process lifetime; tests can provide a deterministic
 * dispatcher and close it without creating Android dependencies.
 */
class AppCoroutineScope(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Closeable {
    private val job = SupervisorJob()

    val scope = CoroutineScope(job + dispatcher)

    override fun close() {
        job.cancel()
    }
}
