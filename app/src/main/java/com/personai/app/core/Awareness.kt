package com.personai.app.core

import android.content.Context
import android.util.Log
import com.personai.app.soul.*
import com.personai.app.system.*
import kotlinx.coroutines.*

/**
 * Awareness — bridges device environment to the entity's cognition.
 *
 * Subscribes to SystemBridgeManager's SystemContext StateFlow.
 * Translates environmental signals into soul-relevant events:
 *   Late night + high N → drift toward PENSIVE
 *   Morning + high E    → drift toward CONTENT
 *   Low battery         → trigger proactive thought
 *   Offline             → entity notices the silence
 *
 * Also provides the prompt context string that gets injected
 * into every cognition pipeline call.
 */
class Awareness(spark: SoulSpark, private val context: Context) : Oni(spark) {

    override val id = "awareness"

    override fun wake() {
        reportHealth(OniHealth.RUNNING)
        scope.launch { observeContext() }
        Log.i(id, "Online — device perception active")
    }

    private suspend fun observeContext() {
        SystemBridgeManager.instance?.systemContext?.collect { ctx ->
            onContextUpdate(ctx)
        }
    }

    private suspend fun onContextUpdate(ctx: SystemContext) {
        val s = soul ?: return

        // Time-based mood influence
        val suggestedMood = when (ctx.timeOfDay) {
            TimeOfDay.LATE_NIGHT, TimeOfDay.EARLY_MORNING ->
                if (s.neural.ocean.neuroticism > 0.55f) Mood.PENSIVE else null
            TimeOfDay.MORNING ->
                if (s.neural.ocean.extraversion > 0.6f) Mood.CONTENT else null
            TimeOfDay.EVENING -> null
            else -> null
        }

        suggestedMood?.let { suggested ->
            // Only nudge if not already in a strong mood state
            if (Math.abs(s.identity.currentMood.valence) < 0.5f &&
                s.identity.currentMood != suggested) {
                spark.setMood(suggested)
                Log.d(id, "Time-based mood suggestion: ${suggested.display}")
            }
        }

        // Low battery — entity notices
        if (ctx.batteryLevel in 1..15 && !ctx.isCharging) {
            spark.proactive.triggerThought("low_battery")
        }

        // Just went offline
        if (!ctx.networkConnected && ctx.networkType == "none") {
            spark.proactive.triggerThought("offline")
        }
    }

    /** Build the environment context string for injection into prompts. */
    fun buildPromptContext(): String =
        SystemBridgeManager.instance?.systemContext?.value?.toPromptContext() ?: ""

    fun currentContext(): SystemContext =
        SystemBridgeManager.instance?.systemContext?.value ?: SystemContext.empty()
}
