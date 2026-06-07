package com.personai.app.core

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * SoulHeartbeatWorker — periodic soul save via WorkManager.
 * Survives process death. Soul is never more than 15 minutes from its last save.
 */
class SoulHeartbeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        SoulSpark.instance?.emergencySave()
        Log.d("Heartbeat", "Soul saved via heartbeat")
        return Result.success()
    }
}
