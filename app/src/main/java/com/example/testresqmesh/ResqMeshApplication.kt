package com.example.testresqmesh

import android.app.Application
import com.example.testresqmesh.core.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ResqMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ResqMeshApplication)
            modules(appModule)
        }
    }
}
