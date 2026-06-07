package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*
import java.util.UUID

/**
 * MemoryManager — tiered memory system.
 *
 * Four tiers:
 *   SURFACE  — hot, in RAM, current session only
 *   DEEP     — Room DB (stub — to be implemented with Room)
 *   ARCHIVE  — .memzip compressed files + lightweight tag index
 *   ANCHOR   — permanent JSON in Soul document, never archived
 *
 * All retention decisions are personality-driven via retentionDays().
 */
class MemoryManager(private val spark: SoulSpark) {

    companion object {
        private const val TAG           = "MemoryManager"
        private const val SURFACE_LIMIT = 50
        private const val MAX_ANCHORS   = 100
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Surface memory — current session, in RAM only
    private val surface = mutableListOf<SurfaceMemory>()

    data class SurfaceMemory(
        val content:   String,
        val timestamp: Long            = System.currentTimeMillis(),
        val tags:      List<String>    = emptyList(),
        val importance: Float          = 0.5f
    )

    // ── Write ─────────────────────────────────────────────────────────────

    fun add(content: String, tags: List<String> = emptyList(), importance: Float = 0.5f) {
        val mem = SurfaceMemory(content, tags = tags, importance = importance)
        surface.add(mem)
        if (surface.size > SURFACE_LIMIT) surface.removeAt(0)

        val soul = spark.soul ?: return
        if (shouldAnchor(soul.neural.ocean, importance)) {
            scope.launch { promoteToAnchor(content, importance, tags) }
        }
    }

    private suspend fun promoteToAnchor(content: String, weight: Float, tags: List<String>) {
        val anchor = MemoryAnchor(
            id              = UUID.randomUUID().toString(),
            summary         = content.take(200),
            emotionalWeight = weight,
            timestamp       = System.currentTimeMillis(),
            tags            = tags
        )
        spark.mutate {
            val updated = (memory.anchors + anchor)
                .sortedByDescending { it.emotionalWeight }
                .take(MAX_ANCHORS)
            copy(memory = memory.copy(anchors = updated))
        }
        Log.i(TAG, "Anchored: ${content.take(60)}")
    }

    // ── Retention formula (personality-driven) ────────────────────────────

    /**
     * How many days this memory should stay in DEEP before archiving.
     *
     * Conscientiousness  — high C retains longer (organized, deliberate)
     * Openness           — high O archives faster (always moving forward)
     * Neuroticism        — high N retains emotional memories much longer
     * Agreeableness      — high A retains relational memories longer
     */
    fun retentionDays(ocean: OceanTraits, memory: SurfaceMemory): Int {
        var days = 30f
        days += ocean.conscientiousness * 20f   // +0 to +20 days
        days -= ocean.openness          * 10f   // -0 to -10 days
        if (memory.importance > 0.5f)
            days += ocean.neuroticism   * 15f   // emotional stickiness
        if (memory.tags.any { it.startsWith("person:") })
            days += ocean.agreeableness * 10f   // relational memory
        return days.coerceIn(7f, 90f).toInt()
    }

    // ── Anchor formation (personality-driven) ─────────────────────────────

    /**
     * Whether a memory should become a permanent anchor.
     * Threshold shifts based on personality:
     *   High N — lower threshold (everything feels significant)
     *   High C — higher threshold (anchors are deliberate, few)
     *   High A — relational moments anchor more easily
     *   High O — novel/first-time experiences anchor more easily
     */
    fun shouldAnchor(ocean: OceanTraits, emotionalWeight: Float,
                     tags: List<String> = emptyList()): Boolean {
        val threshold = 0.70f -
            (ocean.neuroticism       * 0.20f) +
            (ocean.conscientiousness * 0.15f)
        val boost = when {
            tags.any { it.startsWith("person:") } -> ocean.agreeableness * 0.10f
            tags.contains("type:first")            -> ocean.openness      * 0.10f
            else                                    -> 0f
        }
        return (emotionalWeight + boost) >= threshold
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /** Returns recent surface memories for prompt context. */
    fun recentSurface(limit: Int = 5): List<SurfaceMemory> =
        surface.takeLast(limit)

    /** Returns anchors relevant to a topic (simple tag match — vector search is future). */
    fun relevantAnchors(topic: String, limit: Int = 3): List<MemoryAnchor> =
        spark.soul?.memory?.anchors
            ?.filter { anchor -> anchor.tags.any { it.contains(topic, ignoreCase = true) } }
            ?.sortedByDescending { it.emotionalWeight }
            ?.take(limit)
            ?: emptyList()

    fun clearSurface() { surface.clear() }
}
