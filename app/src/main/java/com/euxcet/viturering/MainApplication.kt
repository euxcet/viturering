package com.euxcet.viturering

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.dmitrybrant.modelviewer.ModuleProxy
import com.hcifuture.producer.sensor.NuixSensorManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        const val TAG = "MainApplication"
        lateinit var instance: MainApplication
    }

    @Inject
    lateinit var nuixSensorManager: NuixSensorManager

    val mainHandler: Handler by lazy {  Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ModuleProxy.initModule(this)
    }
}