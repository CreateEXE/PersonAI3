package com.personai.app

import android.app.Application
import android.util.Log
import com.personai.app.core.SoulSpark
import com.personai.app.overlay.OverlayManager
import com.personai.app.system.SystemBridgeManager

class PersonAIApplication : Application() {

    companion object {
        private const val TAG = "PersonAI"
        lateinit var soulSpark:    SoulSpark
            private set
        lateinit var systemBridge: SystemBridgeManager
            private set
        var overlayManager: OverlayManager? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PersonAI starting")

        systemBridge  = SystemBridgeManager(applicationContext)
        soulSpark     = SoulSpark(applicationContext)
        soulSpark.wake()

        // Start overlay once permission is available
        // Called again from MainActivity after user grants SYSTEM_ALERT_WINDOW
        tryStartOverlay()
    }

    fun tryStartOverlay() {
        val om = OverlayManager(applicationContext, soulSpark)
        if (om.canDrawOverlays()) {
            om.start()
            overlayManager = om
            Log.i(TAG, "Overlay started")
        } else {
            Log.w(TAG, "Overlay permission not yet granted — grant in Settings > Apps > Special App Access")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        overlayManager?.stop()
        soulSpark.sleep()
    }
}
