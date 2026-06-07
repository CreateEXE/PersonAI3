package com.personai.app.system

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PersonAINotificationService : NotificationListenerService() {
    companion object {
        private const val TAG = "PersonAINotifications"
        @Volatile var isRunning = false
        private val MESSAGING = setOf("com.whatsapp","com.google.android.apps.messaging",
            "org.telegram.messenger","com.discord","com.facebook.orca",
            "com.instagram.android","com.google.android.gm","com.slack")
    }
    private val pm: PackageManager by lazy { applicationContext.packageManager }

    override fun onListenerConnected() {
        super.onListenerConnected(); isRunning = true
        try { activeNotifications?.forEach { process(it, false) } } catch (_: Exception) {}
        SystemBridgeManager.instance?.onNotificationListenerConnected()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification)  { process(sbn, true) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { SystemBridgeManager.instance?.onNotificationDismissed(sbn.packageName) }
    override fun onListenerDisconnected() { super.onListenerDisconnected(); isRunning = false }

    private fun process(sbn: StatusBarNotification, isNew: Boolean) {
        val extras = sbn.notification.extras ?: return
        val pkg    = sbn.packageName
        val title  = extras.getString("android.title")?.trim() ?: ""
        val text   = extras.getCharSequence("android.text")?.toString()?.trim() ?: ""
        if (title.isEmpty() && text.isEmpty()) return
        if (pkg == "android" || pkg == packageName) return
        SystemBridgeManager.instance?.onNotificationReceived(
            NotificationInfo(pkg, try { pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString() }
                catch (e: Exception) { pkg.substringAfterLast(".") }, title, text, sbn.postTime, pkg in MESSAGING), isNew)
    }
}
