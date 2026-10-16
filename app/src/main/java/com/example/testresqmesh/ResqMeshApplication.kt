package com.example.testresqmesh

import android.app.Application
import com.example.testresqmesh.core.di.appModule
import com.example.testresqmesh.core.utils.PerfTrace
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ResqMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PerfTrace.installMainLooperWatchdog()
        startKoin {
            androidContext(this@ResqMeshApplication)
            modules(appModule)
        }
    }
}
