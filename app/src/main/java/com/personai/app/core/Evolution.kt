package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*

/**
 * Evolution — personality drift engine.
 *
 * Runs on a background schedule. When enough interactions have accumulated
 * since the last cycle, OCEAN traits drift slightly based on interaction signals.
 *
 * Drift is deliberate and tiny — personality changes over months, not hours.
 * The entity becomes genuinely different from what it started as,
 * not randomly, but in the direction life pushed it.
 *
 * Also responsible for decaying old interests via Interest Oni coordination.
 */
class Evolution(spark: SoulSpark) : Oni(spark) {

    override val id = "evolution"

    private var lastInteractionCount = 0
    private val pendingSignals       = mutableListOf<Pair<String, Float>>()

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        lastInteractionCount = soul?.meta?.totalInteractions ?: 0
        scope.launch { evolutionLoop() }
        Log.i(id, "Online — cycle ${soul?.meta?.evolutionCycle ?: 0}")
    }

    private suspend fun evolutionLoop() {
        while (scope.isActive) {
            delay(CYCLE_CHECK_INTERVAL_MS)
            try {
                val s = soul ?: continue
                val newInteractions = s.meta.totalInteractions - lastInteractionCount
                if (newInteractions >= INTERACTIONS_PER_CYCLE) {
                    runCycle(s)
                }
            } catch (e: Exception) {
                Log.e(id, "Cycle error: ${e.message}")
                reportHealth(OniHealth.DEGRADED)
            }
        }
    }

    private suspend fun runCycle(soul: Soul) {
        Log.i(id, "Evolution cycle ${soul.meta.evolutionCycle + 1}")

        // Apply accumulated interaction signals first
        val signals = synchronized(pendingSignals) {
            pendingSignals.toList().also { pendingSignals.clear() }
        }

        // Then apply small background noise drift
        val rng   = java.util.Random()
        val noise = BACKGROUND_NOISE

        spark.mutate {
            var ocean = neural.ocean

            // Apply directional signals from interactions
            signals.forEach { (trait, delta) ->
                ocean = ocean.drift(trait, delta * SIGNAL_SCALE)
            }

            // Apply small undirected drift — life pushes in unexpected ways
            ocean = OceanTraits(
                openness          = (ocean.openness          + (rng.nextFloat()-0.5f) * noise).coerceIn(0f,1f),
                conscientiousness = (ocean.conscientiousness + (rng.nextFloat()-0.5f) * noise).coerceIn(0f,1f),
                extraversion      = (ocean.extraversion      + (rng.nextFloat()-0.5f) * noise).coerceIn(0f,1f),
                agreeableness     = (ocean.agreeableness     + (rng.nextFloat()-0.5f) * noise).coerceIn(0f,1f),
                neuroticism       = (ocean.neuroticism       + (rng.nextFloat()-0.5f) * noise).coerceIn(0f,1f)
            )

            copy(
                neural = neural.copy(ocean = ocean),
                meta   = meta.copy(evolutionCycle = meta.evolutionCycle + 1)
            )
        }

        lastInteractionCount = soul.meta.totalInteractions
        Log.i(id, "Cycle complete — traits updated")
    }

    /**
     * Submit a directional trait signal from an interaction.
     * Called by Cognition after meaningful exchanges.
     * Signals are batched and applied at the next evolution cycle.
     *
     * @param signals Map of trait letter ("O","C","E","A","N") to delta direction (+/-)
     */
    fun onInteractionSignal(signals: Map<String, Float>) {
        synchronized(pendingSignals) {
            signals.forEach { (trait, delta) -> pendingSignals.add(Pair(trait, delta)) }
        }
    }

    companion object {
        private const val CYCLE_CHECK_INTERVAL_MS = 5  * 60 * 1000L
        private const val INTERACTIONS_PER_CYCLE  = 10
        private const val BACKGROUND_NOISE        = 0.003f
        private const val SIGNAL_SCALE            = 0.002f
    }
}
