package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import com.personai.app.core.SoulFusion
import kotlinx.coroutines.*

/**
 * Cognition — LLM pipeline orchestrator.
 *
 * Uses SoulFusion to build the soul-encoded system prompt.
 * Routes calls through InferenceQueue at CRITICAL priority.
 * Signals Evolution after meaningful interactions.
 */
class Cognition(spark: SoulSpark) : Oni(spark) {

    override val id = "cognition"

    enum class Mode { QUICK, PERSONA, FULL, DREAM }

    private val history = mutableListOf<Message>()
    data class Message(val role: String, val content: String)

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        Log.i(id, "Online")
    }

    suspend fun process(input: String, mode: Mode = Mode.PERSONA): String {
        val s = soul ?: return "[soul not loaded]"
        spark.inferenceQueue.cancelBackground()

        val envCtx     = spark.awareness.buildPromptContext()
        val systemPart = SoulFusion.buildSystemPrompt(s, envCtx, mode.name)
        val fullPrompt = buildFullPrompt(systemPart, input)

        val baseParams = when (mode) {
            Mode.QUICK   -> InferenceParams.PRECISE
            Mode.PERSONA -> InferenceParams.CONVERSATIONAL
            Mode.FULL    -> InferenceParams.REFLECTIVE
            Mode.DREAM   -> InferenceParams.DREAM
        }

        return try {
            val response = spark.infer(fullPrompt, InferenceQueue.Priority.CRITICAL, baseParams)

            history.add(Message("user", input))
            history.add(Message("entity", response))
            if (history.size > MAX_HISTORY * 2) { history.removeAt(0); history.removeAt(0) }

            spark.recordInteraction()
            spark.evolution.onInteractionSignal(deriveSignals(input, response))
            spark.emotion.onPositiveInteraction()

            response
        } catch (e: Exception) {
            Log.e(id, "Pipeline error: ${e.message}")
            reportHealth(OniHealth.DEGRADED)
            "[pipeline error]"
        }
    }

    private fun buildFullPrompt(system: String, input: String): String = buildString {
        append(system)
        history.takeLast(MAX_HISTORY * 2).forEach { append("<|${it.role}|>\n${it.content}\n") }
        append("<|user|>\n$input\n<|entity|>\n")
    }

    private fun deriveSignals(input: String, response: String): Map<String, Float> {
        val combined = (input + response).lowercase()
        val signals  = mutableMapOf<String, Float>()
        if (combined.contains(Regex("why|how|curious|wonder|interesting"))) signals["O"] = 1f
        if (combined.contains(Regex("plan|organize|careful|detail")))        signals["C"] = 1f
        if (combined.contains(Regex("feel|emotion|care|love|worry")))        signals["A"] = 0.5f
        if (combined.contains(Regex("excited|amazing|great|fun")))           signals["E"] = 0.5f
        return signals
    }

    fun clearHistory() { history.clear() }

    companion object { private const val MAX_HISTORY = 10 }
}
