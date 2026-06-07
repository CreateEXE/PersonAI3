package com.personai.app.core

import android.util.Log
import kotlinx.coroutines.*; import kotlinx.coroutines.channels.Channel

class InferenceQueue {
    companion object { private const val TAG = "InferenceQueue" }

    enum class Priority { CRITICAL, NORMAL, BACKGROUND }

    data class Request(
        val id:       String,
        val priority: Priority,
        val params:   InferenceParams,
        val prompt:   String,
        val result:   CompletableDeferred<String>
    )

    private val critical   = Channel<Request>(Channel.UNLIMITED)
    private val normal     = Channel<Request>(Channel.UNLIMITED)
    private val background = Channel<Request>(Channel.UNLIMITED)
    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile var isRunning         = false; private set
    @Volatile var currentRequestId: String? = null; private set
    val isBusy: Boolean get()       = currentRequestId != null

    var executor: (suspend (Request) -> String)? = null

    fun start() { if (isRunning) return; isRunning = true; scope.launch { loop() }; Log.i(TAG, "Started") }
    fun stop()  { isRunning = false }

    suspend fun submit(prompt: String, priority: Priority, params: InferenceParams,
                       id: String = System.currentTimeMillis().toString()): String {
        val d = CompletableDeferred<String>()
        val r = Request(id, priority, params, prompt, d)
        when (priority) {
            Priority.CRITICAL   -> critical.send(r)
            Priority.NORMAL     -> normal.send(r)
            Priority.BACKGROUND -> background.send(r)
        }
        Log.d(TAG, "Queued [$priority] $id"); return d.await()
    }

    fun cancelBackground() {
        var n = 0
        while (true) { val r = background.tryReceive(); if (r.isSuccess) { r.getOrNull()?.result?.cancel(); n++ } else break }
        if (n > 0) Log.d(TAG, "Cancelled $n background requests")
    }

    private suspend fun loop() {
        while (isRunning) {
            val req = critical.tryReceive().getOrNull()
                ?: normal.tryReceive().getOrNull()
                ?: background.tryReceive().getOrNull()
            if (req == null) { delay(50); continue }
            currentRequestId = req.id
            try {
                val exec = executor ?: run { req.result.completeExceptionally(IllegalStateException("No executor")); continue }
                req.result.complete(exec(req))
            } catch (e: Exception) { Log.e(TAG, "Inference error: ${e.message}"); req.result.completeExceptionally(e) }
            finally { currentRequestId = null }
        }
    }
}
