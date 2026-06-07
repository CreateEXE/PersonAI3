package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*

/**
 * Emotion — mood state management and inference temperature shaping.
 *
 * Implements OniHook to adjust inference parameters based on current mood:
 *   ELATED      → higher temperature (more expressive, surprising)
 *   PENSIVE     → lower temperature (measured, deliberate)
 *   UNSETTLED   → higher temperature, slightly erratic
 *   MELANCHOLIC → lower temperature, subdued
 *
 * Neuroticism amplifies mood's effect — high N entities feel everything sharper.
 * Extraversion shapes response length — high E speaks more.
 *
 * Mood naturally drifts toward neutral over time.
 * High N entities drift back slower — they hold onto feelings longer.
 */
class Emotion(spark: SoulSpark) : Oni(spark), OniHook {

    override val id = "emotion"

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        scope.launch { moodDriftLoop() }
        Log.i(id, "Online — current mood: ${soul?.identity?.currentMood?.display ?: "unknown"}")
    }

    // ── OniHook ──────────────────────────────────────────────────────────

    override fun apply(params: InferenceParams, soul: Soul): InferenceParams {
        val mood = soul.identity.currentMood
        val n    = soul.neural.ocean.neuroticism
        val e    = soul.neural.ocean.extraversion

        // Base temperature delta from mood valence
        val moodDelta = when (mood) {
            Mood.ELATED      ->  0.25f
            Mood.CONTENT     ->  0.05f
            Mood.CURIOUS     ->  0.15f
            Mood.NEUTRAL     ->  0.00f
            Mood.PENSIVE     -> -0.10f
            Mood.UNSETTLED   ->  0.20f
            Mood.MELANCHOLIC -> -0.15f
        }

        // Neuroticism amplifies — volatile entity feels more intensely
        val amplified = moodDelta * (1f + n * 0.5f)

        // Extraversion nudges max response length
        val tokenAdjust = ((e - 0.5f) * 80f).toInt()

        return params.copy(
            temperature = (params.temperature + amplified).coerceIn(0.3f, 1.4f),
            maxTokens   = (params.maxTokens + tokenAdjust).coerceIn(64, 512)
        )
    }

    // ── Mood drift ───────────────────────────────────────────────────────

    private suspend fun moodDriftLoop() {
        while (scope.isActive) {
            delay(DRIFT_CHECK_INTERVAL_MS)
            val s = soul ?: continue
            val mood = s.identity.currentMood
            if (mood == Mood.NEUTRAL) continue

            // High N drifts back to neutral slower
            val driftChance = (1f - s.neural.ocean.neuroticism * 0.7f) * 0.4f
            if (Math.random().toFloat() < driftChance) {
                val calmer = calmToward(mood)
                if (calmer != mood) {
                    spark.setMood(calmer)
                    Log.d(id, "Mood drift: ${mood.display} → ${calmer.display}")
                }
            }
        }
    }

    private fun calmToward(mood: Mood): Mood = when (mood) {
        Mood.ELATED      -> Mood.CONTENT
        Mood.CONTENT     -> Mood.NEUTRAL
        Mood.CURIOUS     -> Mood.NEUTRAL
        Mood.NEUTRAL     -> Mood.NEUTRAL
        Mood.PENSIVE     -> Mood.NEUTRAL
        Mood.UNSETTLED   -> Mood.PENSIVE
        Mood.MELANCHOLIC -> Mood.PENSIVE
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun onPositiveInteraction() { scope.launch { elevate() } }
    fun onNegativeInteraction() { scope.launch { lower() } }

    private suspend fun elevate() {
        val current = soul?.identity?.currentMood ?: return
        val next = when (current) {
            Mood.MELANCHOLIC -> Mood.PENSIVE
            Mood.PENSIVE     -> Mood.NEUTRAL
            Mood.NEUTRAL     -> Mood.CURIOUS
            Mood.CURIOUS     -> Mood.CONTENT
            Mood.CONTENT     -> Mood.ELATED
            Mood.UNSETTLED   -> Mood.NEUTRAL
            Mood.ELATED      -> Mood.ELATED
        }
        if (next != current) spark.setMood(next)
    }

    private suspend fun lower() {
        val current = soul?.identity?.currentMood ?: return
        val next = when (current) {
            Mood.ELATED      -> Mood.CONTENT
            Mood.CONTENT     -> Mood.NEUTRAL
            Mood.CURIOUS     -> Mood.PENSIVE
            Mood.NEUTRAL     -> Mood.PENSIVE
            Mood.PENSIVE     -> Mood.UNSETTLED
            Mood.UNSETTLED   -> Mood.MELANCHOLIC
            Mood.MELANCHOLIC -> Mood.MELANCHOLIC
        }
        if (next != current) spark.setMood(next)
    }

    companion object {
        private const val DRIFT_CHECK_INTERVAL_MS = 10 * 60 * 1000L // every 10 min
    }
}
