package com.example.testresqmesh

import android.app.Application
import com.example.testresqmesh.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ResqMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ResqMeshApplication)
            modules(appModules)
        }
    }
}
