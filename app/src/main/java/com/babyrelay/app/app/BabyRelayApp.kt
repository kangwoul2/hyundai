package com.babyrelay.app.app

import android.app.Application

class BabyRelayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
