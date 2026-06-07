package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*

/**
 * Interest — curiosity and autonomous learning engine.
 *
 * Watches the soul's interest graph. When a topic has significant weight
 * and the queue is idle, pursues it — generating thoughts, questions,
 * and observations about what the entity is currently fascinated by.
 *
 * Interests grow through engagement and decay through neglect.
 * The entity can only hold MAX_INTERESTS topics simultaneously —
 * new interests displace old ones by weight.
 *
 * Interest pursuit runs at BACKGROUND priority — never interrupts anything.
 */
class Interest(spark: SoulSpark) : Oni(spark) {

    override val id = "interest"

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        scope.launch { interestLoop() }
        Log.i(id, "Online — tracking ${soul?.neural?.interests?.size ?: 0} interests")
    }

    private suspend fun interestLoop() {
        while (scope.isActive) {
            delay(PURSUIT_INTERVAL_MS)
            if (spark.inferenceQueue.isBusy) continue

            val soul = soul ?: continue
            val top  = soul.neural.interests
                .filter { it.weight > PURSUIT_THRESHOLD }
                .maxByOrNull { it.weight } ?: continue

            pursue(soul, top)
        }
    }

    private suspend fun pursue(soul: Soul, interest: com.personai.app.soul.Interest) {
        Log.d(id, "Pursuing: ${interest.topic} (weight ${interest.weight.fmt()})")

        val prompt = buildString {
            appendLine("You are ${soul.identity.name}.")
            appendLine("You are currently fascinated by: ${interest.topic}")
            appendLine("Mood: ${soul.identity.currentMood.display}")
            appendLine()
            appendLine("Generate a specific thought, question, or observation about this topic.")
            appendLine("Make it genuine and personal — filtered through who you are, not generic.")
            appendLine("One or two sentences.")
        }

        try {
            val thought = spark.infer(
                prompt     = prompt,
                priority   = InferenceQueue.Priority.BACKGROUND,
                baseParams = InferenceParams.CREATIVE.copy(maxTokens = 100)
            )
            if (thought.isNotBlank()) {
                Log.d(id, "Synthesis: ${thought.take(60)}")
                spark.proactive.onThought?.invoke(thought)
                reinforce(interest.topic, ENGAGEMENT_BOOST)
            }
        } catch (e: Exception) {
            Log.w(id, "Pursuit failed: ${e.message}")
        }
    }

    // ── Interest management ───────────────────────────────────────────────

    /**
     * Called by Cognition when a topic emerges in conversation.
     * Grows or creates an interest entry.
     */
    fun onTopicEngaged(topic: String, delta: Float = 0.05f) {
        scope.launch { reinforce(topic, delta) }
    }

    private suspend fun reinforce(topic: String, delta: Float) {
        spark.mutate {
            val existing = neural.interests.find { it.topic.equals(topic, ignoreCase = true) }
            val updated  = existing?.copy(
                weight = (existing.weight + delta).coerceIn(0f, 1f),
                lastEngaged = System.currentTimeMillis()
            ) ?: com.personai.app.soul.Interest(
                topic = topic, weight = delta.coerceIn(0f, 1f),
                lastEngaged = System.currentTimeMillis(), origin = "conversation"
            )
            val newList = neural.interests
                .filter { !it.topic.equals(topic, ignoreCase = true) }
                .plus(updated)
                .sortedByDescending { it.weight }
                .take(MAX_INTERESTS)
            copy(neural = neural.copy(interests = newList))
        }
    }

    /** Decay interests that haven't been engaged recently. */
    suspend fun decayAll() {
        val now = System.currentTimeMillis()
        spark.mutate {
            copy(neural = neural.copy(
                interests = neural.interests.map { i ->
                    val ageMs = now - i.lastEngaged
                    if (ageMs > DECAY_THRESHOLD_MS) i.copy(weight = (i.weight - DECAY_RATE).coerceIn(0f, 1f))
                    else i
                }.filter { it.weight > PRUNE_THRESHOLD }
            ))
        }
        Log.d(id, "Interest decay applied")
    }

    private fun Float.fmt() = "%.2f".format(this)

    companion object {
        private const val PURSUIT_INTERVAL_MS  = 20 * 60 * 1000L
        private const val PURSUIT_THRESHOLD    = 0.35f
        private const val ENGAGEMENT_BOOST     = 0.02f
        private const val MAX_INTERESTS        = 20
        private const val DECAY_THRESHOLD_MS   = 7L * 24 * 60 * 60 * 1000L // 7 days
        private const val DECAY_RATE           = 0.01f
        private const val PRUNE_THRESHOLD      = 0.05f
    }
}
