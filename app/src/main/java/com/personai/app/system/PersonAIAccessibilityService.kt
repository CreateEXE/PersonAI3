package com.personai.app.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PersonAIAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "PersonAIAccessibility"
        @Volatile var isRunning = false; @Volatile var latestScreenText = ""
        @Volatile var latestForegroundPackage = ""
    }
    private val pm: PackageManager by lazy { applicationContext.packageManager }

    override fun onServiceConnected() {
        super.onServiceConnected(); isRunning = true
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC; notificationTimeout = 200L
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "Connected"); SystemBridgeManager.instance?.onAccessibilityConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == latestForegroundPackage) return
                latestForegroundPackage = pkg
                SystemBridgeManager.instance?.onAppSwitch(pkg, appLabel(pkg))
                updateScreenText()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                SystemBridgeManager.instance?.onContentChanged()
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val text = event.text.joinToString(" ")
                if (text.isNotBlank()) SystemBridgeManager.instance?.onAccessibilityNotification(pkg, text)
            }
        }
    }

    fun updateScreenText() {
        try { val root = rootInActiveWindow ?: return; latestScreenText = extractText(root).take(500); root.recycle() }
        catch (e: Exception) { Log.w(TAG, "Text extraction failed: ${e.message}") }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); isRunning = false; SystemBridgeManager.instance?.onAccessibilityDisconnected() }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun go(n: AccessibilityNodeInfo, d: Int) {
            if (d > 12) return
            (n.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: n.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() })?.let { sb.append(it).append(" ") }
            for (i in 0 until n.childCount) n.getChild(i)?.let { go(it, d+1); it.recycle() }
        }
        go(node, 0); return sb.toString().trim()
    }

    private fun appLabel(pkg: String) = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        catch (e: Exception) { pkg.substringAfterLast(".") }
}
