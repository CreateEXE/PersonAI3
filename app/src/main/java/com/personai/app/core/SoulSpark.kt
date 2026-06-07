package com.personai.app.core

import android.content.Context
import android.util.Log
import androidx.work.*
import com.personai.app.soul.*
import com.personai.app.system.SystemBridgeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

/**
 * SoulSpark — the central nervous system of a PersonAI entity.
 *
 * Owns everything:
 *   - SoulEngine (persistence)
 *   - LlamaEngine (inference)
 *   - InferenceQueue (all LLM access)
 *   - All Oni (wakes, monitors, restarts)
 *   - Emergency save / WorkManager heartbeat
 *
 * Boot sequence:
 *   wake() → loadSoul() or await genesis → loadModel → wakeOni → ready
 */
class SoulSpark(val context: Context) {

    companion object {
        const val TAG = "SoulSpark"
        @Volatile var instance: SoulSpark? = null
            private set
    }

    // ── Core engines ───────────────────────────────────────────────────────
    private val engine        = SoulEngine(context)
    val llamaEngine           = LlamaEngine(context)
    val inferenceQueue        = InferenceQueue()
    val soulState: StateFlow<SoulEngine.SoulState> = engine.soulState
    val soul: Soul? get()     = engine.soul

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Oni roster ─────────────────────────────────────────────────────────
    val emotion   = Emotion(this)
    val evolution = Evolution(this)
    val awareness = Awareness(this, context)
    val cognition = Cognition(this)
    val proactive = Proactive(this)
    val interest  = Interest(this)
    val mobility  = Mobility(this)

    private val roster: List<Oni> get() = listOf(
        emotion, evolution, awareness, cognition, proactive, interest, mobility)

    init { instance = this }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun wake() {
        Log.i(TAG, "SoulSpark waking")
        engine.wake()
        scope.launch {
            engine.soulState.first { it !is SoulEngine.SoulState.Loading }

            // Load LLM model
            val modelLoaded = llamaEngine.loadModel()
            if (modelLoaded) {
                wireInferenceQueue()
                Log.i(TAG, "LlamaEngine online")
            } else {
                Log.w(TAG, "Model not found — inference will return stubs until model is placed")
                wireStubExecutor()
            }

            wakeOni()
            startHeartbeat()
            Log.i(TAG, "SoulSpark online")
        }
    }

    fun sleep() {
        Log.i(TAG, "SoulSpark sleeping")
        engine.emergencySave()
        roster.reversed().forEach { runCatching { it.sleep() } }
        inferenceQueue.stop()
        llamaEngine.free()
        scope.cancel()
        instance = null
    }

    fun emergencySave() { engine.emergencySave() }

    suspend fun genesis(archetype: Archetype, name: String = "unnamed"): Soul {
        val s = engine.genesis(archetype, name)
        wakeOni()
        return s
    }

    // ── Oni management ─────────────────────────────────────────────────────

    private fun wakeOni() {
        roster.forEach { oni ->
            runCatching { oni.wake() }
                .onSuccess { Log.i(TAG, "[${oni.id}] online") }
                .onFailure { Log.e(TAG, "[${oni.id}] wake failed: ${it.message}") }
        }
    }

    fun onOniHealthReport(id: String, status: OniHealth) {
        if (status == OniHealth.DEAD) {
            Log.w(TAG, "[$id] died — restarting in 2s")
            scope.launch {
                delay(2000L)
                roster.find { it.id == id }?.runCatching { wake() }
            }
        }
    }

    // ── Soul mutations ─────────────────────────────────────────────────────

    suspend fun mutate(block: Soul.() -> Soul)          = engine.mutate(block)
    suspend fun driftTrait(trait: String, delta: Float) = engine.driftTrait(trait, delta)
    suspend fun setMood(mood: Mood)                     = engine.setMood(mood)
    suspend fun setObsession(topic: String?)            = engine.setObsession(topic)
    suspend fun recordInteraction()                     = engine.recordInteraction()

    // ── Inference ──────────────────────────────────────────────────────────

    /**
     * Submit inference with all Oni hooks applied to params.
     * This is the single entry point for all LLM calls.
     */
    suspend fun infer(
        prompt:     String,
        priority:   InferenceQueue.Priority = InferenceQueue.Priority.NORMAL,
        baseParams: InferenceParams         = InferenceParams.CONVERSATIONAL
    ): String {
        val params = applyHooks(baseParams)
        return inferenceQueue.submit(prompt, priority, params)
    }

    private fun applyHooks(base: InferenceParams): InferenceParams {
        val s = soul ?: return base
        return roster.filterIsInstance<OniHook>()
            .fold(base) { p, hook -> runCatching { hook.apply(p, s) }.getOrDefault(p) }
    }

    // ── Inference queue wiring ─────────────────────────────────────────────

    private fun wireInferenceQueue() {
        inferenceQueue.executor = { request -> llamaEngine.infer(request) }
        inferenceQueue.start()
        Log.i(TAG, "InferenceQueue wired to LlamaEngine")
    }

    private fun wireStubExecutor() {
        inferenceQueue.executor = { req ->
            val name = soul?.identity?.name ?: "entity"
            "[Model not loaded. Place model.gguf at ${llamaEngine.defaultModelPath()}]"
        }
        inferenceQueue.start()
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        val req = PeriodicWorkRequestBuilder<SoulHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "soul_heartbeat", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
