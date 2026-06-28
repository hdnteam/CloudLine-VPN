package com.hdnteam.cloudlinevpn

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CloudLineApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i("CloudLineApp", "Application created")
        // Minimal init — no heavy work here
        try {
            com.hdnteam.cloudlinevpn.util.CrashHandler.install(this)
        } catch (e: Throwable) {
            Log.e("CloudLineApp", "CrashHandler install failed: ${e.message}")
        }
    }
}
