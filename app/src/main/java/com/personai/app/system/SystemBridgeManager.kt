package com.personai.app.system

import android.app.usage.UsageStatsManager
import android.content.*; import android.net.ConnectivityManager; import android.net.NetworkCapabilities
import android.os.BatteryManager; import android.os.Build; import android.util.Log
import kotlinx.coroutines.*; import kotlinx.coroutines.flow.*
import java.util.Calendar; import java.util.concurrent.TimeUnit

class SystemBridgeManager(private val context: Context) {
    companion object {
        private const val TAG = "SystemBridge"; private const val DEBOUNCE = 2000L
        @Volatile var instance: SystemBridgeManager? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _ctx  = MutableStateFlow(SystemContext.empty())
    val systemContext: StateFlow<SystemContext> = _ctx.asStateFlow()
    private val notifications = mutableListOf<NotificationInfo>()
    private var contentPending = false

    init {
        instance = this; registerReceivers()
        scope.launch { while (true) { delay(30_000L); refresh() } }
    }

    fun onAccessibilityConnected()    { refresh() }
    fun onAccessibilityDisconnected() {}
    fun onNotificationListenerConnected() {}

    fun onAppSwitch(pkg: String, label: String) {
        val recent = (_ctx.value.recentApps + pkg).takeLast(5)
        update { copy(foregroundApp=pkg, foregroundAppLabel=label, recentApps=recent,
            lastAppSwitchMs=System.currentTimeMillis(), screenTextSummary="") }
    }
    fun onContentChanged() {
        if (contentPending) return; contentPending = true
        scope.launch { delay(DEBOUNCE); contentPending = false
            update { copy(screenTextSummary = PersonAIAccessibilityService.latestScreenText) } }
    }
    fun onAccessibilityNotification(pkg: String, text: String) {}
    fun onNotificationReceived(info: NotificationInfo, isNew: Boolean) {
        synchronized(notifications) {
            notifications.removeAll { it.packageName == info.packageName && it.title == info.title }
            notifications.add(info); if (notifications.size > 50) notifications.removeAt(0)
        }
        val s = notifications.toList(); update { copy(activeNotifications=s, unreadCount=s.size) }
    }
    fun onNotificationDismissed(pkg: String) {
        synchronized(notifications) { notifications.removeAll { it.packageName == pkg } }
        val s = notifications.toList(); update { copy(activeNotifications=s, unreadCount=s.size) }
    }

    private fun refresh() {
        val (bl,ch) = battery(); val (nc,nt) = network(); val (mins,top) = usage()
        update { copy(batteryLevel=bl, isCharging=ch, networkConnected=nc, networkType=nt,
            timeOfDay=SystemContext.currentTimeOfDay(),
            hourOfDay=Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            isWeekend=SystemContext.isWeekendNow(), screenTimeTodayMin=mins, topAppToday=top,
            lastUpdated=System.currentTimeMillis()) }
    }
    private fun battery(): Pair<Int,Boolean> {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE,100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS,-1) ?: -1
        return Pair(if(s>0) l*100/s else l, st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL)
    }
    private fun network(): Pair<Boolean,String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return Pair(false,"none")
        val caps = cm.getNetworkCapabilities(net) ?: return Pair(false,"none")
        return Pair(true, when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other" })
    }
    private fun usage(): Pair<Int,String> = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now-TimeUnit.HOURS.toMillis(24), now)
        val filtered = stats?.filter { it.packageName != "android" && it.totalTimeInForeground > 0 } ?: emptyList()
        Pair((filtered.sumOf { it.totalTimeInForeground }/60000).toInt(),
             filtered.maxByOrNull { it.totalTimeInForeground }?.packageName ?: "")
    } catch (_: Exception) { Pair(0,"") }

    private fun registerReceivers() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) { when(i.action) {
                Intent.ACTION_BATTERY_CHANGED -> { val (l,c)=battery(); update { copy(batteryLevel=l,isCharging=c) } }
                ConnectivityManager.CONNECTIVITY_ACTION -> { val (c,t)=network(); update { copy(networkConnected=c,networkType=t) } }
                Intent.ACTION_SCREEN_OFF -> update { copy(screenOn=false) }
                Intent.ACTION_SCREEN_ON  -> update { copy(screenOn=true) }
            }}
        }
        context.registerReceiver(r, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
    }
    private fun update(b: SystemContext.() -> SystemContext) { _ctx.value = _ctx.value.b() }
    fun destroy() { if (instance===this) instance=null }
}
