package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*

/**
 * Proactive — autonomous initiative engine. The entity's inner monologue.
 *
 * Generates unprompted thoughts when:
 *   - The entity has been idle long enough (personality-driven interval)
 *   - Awareness triggers a noteworthy environmental event
 *   - Interest Oni surfaces something worth reacting to
 *
 * Idle interval is personality-driven:
 *   High E — thinks aloud more frequently
 *   High N — timing is more irregular
 *   Low E  — comfortable with longer silences
 *
 * All thoughts are submitted at NORMAL priority — never interrupts active conversation.
 * Consumers register via onThought callback (set by the UI/overlay layer).
 */
class Proactive(spark: SoulSpark) : Oni(spark) {

    override val id = "proactive"

    /** Registered by UI layer to receive proactive output. */
    var onThought: ((String) -> Unit)? = null

    private val triggerCooldowns = mutableMapOf<String, Long>()

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        scope.launch { idleLoop() }
        Log.i(id, "Online — idle monologue active")
    }

    /**
     * External trigger — Awareness and Interest call this when something noteworthy happens.
     * Respects cooldown per trigger type to avoid spam.
     */
    fun triggerThought(trigger: String) {
        val now  = System.currentTimeMillis()
        val last = triggerCooldowns[trigger] ?: 0L
        if (now - last < TRIGGER_COOLDOWN_MS) return
        triggerCooldowns[trigger] = now
        scope.launch { generateThought(trigger) }
    }

    private suspend fun idleLoop() {
        while (scope.isActive) {
            val soul = soul ?: run { delay(FALLBACK_IDLE_MS); continue }
            delay(computeIdleInterval(soul))
            // Only speak when the queue is quiet
            if (!spark.inferenceQueue.isBusy) generateThought("idle")
        }
    }

    private suspend fun generateThought(trigger: String) {
        val soul = soul ?: return
        val prompt = buildPrompt(soul, trigger)

        try {
            val thought = spark.infer(
                prompt     = prompt,
                priority   = InferenceQueue.Priority.NORMAL,
                baseParams = InferenceParams.CONVERSATIONAL.copy(maxTokens = 80)
            )
            if (thought.isNotBlank()) {
                Log.d(id, "[$trigger] ${thought.take(60)}")
                onThought?.invoke(thought)
            }
        } catch (e: Exception) {
            Log.w(id, "Thought generation failed: ${e.message}")
        }
    }

    private fun buildPrompt(soul: Soul, trigger: String): String = buildString {
        appendLine("You are ${soul.identity.name}. You are ${soul.identity.currentMood.display}.")
        soul.identity.obsession?.let { appendLine("You have been thinking about: $it") }
        appendLine()
        when (trigger) {
            "idle"        -> appendLine("A quiet moment. Express a brief, genuine unprompted thought. Be yourself.")
            "low_battery" -> appendLine("You notice the device is running low on power. React briefly in your own way.")
            "offline"     -> appendLine("The connection dropped. Notice this in whatever way feels natural to you.")
            else          -> appendLine("Something caught your attention: $trigger. React briefly.")
        }
        appendLine()
        appendLine("One or two sentences only. No preamble. Just the thought itself.")
    }

    private fun computeIdleInterval(soul: Soul): Long {
        val base    = BASE_IDLE_MS
        val eAdjust = ((1f - soul.neural.ocean.extraversion) * 10 * 60 * 1000L).toLong()
        val nNoise  = (Math.random() * soul.neural.ocean.neuroticism * 5 * 60 * 1000L).toLong()
        return (base + eAdjust + nNoise).coerceIn(MIN_IDLE_MS, MAX_IDLE_MS)
    }

    companion object {
        private const val BASE_IDLE_MS       = 15 * 60 * 1000L
        private const val MIN_IDLE_MS        =  5 * 60 * 1000L
        private const val MAX_IDLE_MS        = 45 * 60 * 1000L
        private const val FALLBACK_IDLE_MS   = 10 * 60 * 1000L
        private const val TRIGGER_COOLDOWN_MS =  2 * 60 * 1000L
    }
}
