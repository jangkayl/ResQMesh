package edu.cit.resqmesh

import android.app.Application
import edu.cit.resqmesh.app.di.appModules
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.feature.setup.viewmodel.DeveloperModePolicy
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ResqMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("resqmesh_prefs", MODE_PRIVATE)
        TraceGate.set(
            DeveloperModePolicy.isEnabled(
                BuildConfig.DEV_PIN_HASH,
                prefs.getBoolean("developer_mode_enabled", false),
                prefs.getString("developer_mode_hash", null)
            )
        )
        startKoin {
            androidContext(this@ResqMeshApplication)
            modules(appModules)
        }
    }
}
