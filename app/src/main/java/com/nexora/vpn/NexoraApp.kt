package com.nexora.vpn

import android.app.Application

class NexoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VpnSession.init(this)
    }
}
