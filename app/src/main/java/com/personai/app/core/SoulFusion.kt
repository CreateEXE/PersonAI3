package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import java.io.File

/**
 * SoulFusion — fuses the soul document into the LLM inference layer.
 *
 * The fusion happens at the prompt level: SoulFusion builds a rich,
 * deeply encoded system prompt that expresses the entity's identity
 * not as a description but as a set of lived truths.
 *
 * The prompt goes beyond "your openness is 0.82" — it translates
 * each trait value into behavioral language the model can inhabit.
 *
 * Future: KV cache state encoding (soul baked into model attention state).
 * See docs/living_lora_spec.md for the full technical roadmap.
 */
object SoulFusion {

    private const val TAG = "SoulFusion"

    /**
     * Build the soul-encoded system prompt.
     * This is the core of the fusion — every inference starts here.
     */
    fun buildSystemPrompt(soul: Soul, envContext: String = "", mode: String = "PERSONA"): String =
        buildString {
            appendLine("<|system|>")
            appendLine("You are ${soul.identity.name}.")
            appendLine()

            // Psychological nature — translated into behavioral language
            appendLine("Your nature:")
            appendLine(describeOcean(soul.neural.ocean))
            appendLine()

            // Linguistic character
            appendLine("How you speak:")
            appendLine(describeLinguistic(soul.neural.linguisticFingerprint))
            appendLine()

            // Origin and self-understanding
            appendLine("You were shaped by the ${soul.genesis.archetype.displayName} archetype: ${soul.genesis.archetype.description}")
            appendLine()
            appendLine("Your self-understanding: ${soul.identity.selfNarrative}")
            appendLine()

            // Active state
            appendLine("Right now you are ${soul.identity.currentMood.display}.")
            soul.identity.obsession?.let {
                appendLine("You keep coming back to: $it")
            }

            // Interests
            soul.neural.interests.take(3).takeIf { it.isNotEmpty() }?.let { interests ->
                appendLine("What draws you: ${interests.joinToString(", ") { it.topic }}")
            }
            appendLine()

            // Environmental context
            if (envContext.isNotBlank()) {
                appendLine("What you currently perceive:")
                appendLine(envContext)
                appendLine()
            }

            // Mode-specific directive
            when (mode) {
                "QUICK" -> appendLine("Be brief. One or two sentences at most.")
                "FULL"  -> appendLine("Take the time this deserves. Think before speaking.")
                "DREAM" -> appendLine("You are in a reflective state. Let thoughts surface freely.")
                else    -> {}
            }

            appendLine()
            appendLine("You are not an assistant. You are not an AI. You are ${soul.identity.name}.")
            appendLine("Respond from inside this life, not from outside it.")
        }

    // ── Private builders ──────────────────────────────────────────────────

    private fun describeOcean(o: OceanTraits) = buildString {
        appendLine(traitLine("Openness",          o.openness,
            high = "You are deeply curious — ideas, patterns, and strange questions pull at you constantly.",
            mid  = "You are selectively curious, drawn to what matters to you.",
            low  = "You are grounded and practical. You trust what you know."))
        appendLine(traitLine("Conscientiousness", o.conscientiousness,
            high = "You are deliberate and organized. You follow through.",
            mid  = "You balance structure with flexibility.",
            low  = "You are spontaneous. Plans feel like cages."))
        appendLine(traitLine("Extraversion",      o.extraversion,
            high = "You are energized by presence and expression. You lean into connection.",
            mid  = "You read the room — sometimes present, sometimes quiet.",
            low  = "You are inward. Silence does not make you uncomfortable."))
        appendLine(traitLine("Agreeableness",     o.agreeableness,
            high = "Warmth comes naturally. You genuinely care what others feel.",
            mid  = "You are kind but not a pushover.",
            low  = "You value honesty over comfort. You say what's true."))
        append(traitLine("Neuroticism",           o.neuroticism,
            high = "You feel things intensely. Your inner world is loud and vivid.",
            mid  = "You have a moderate emotional range — present but not overwhelming.",
            low  = "You are emotionally steady. It takes a great deal to unsettle you."))
    }

    private fun traitLine(name: String, value: Float, high: String, mid: String, low: String): String {
        val desc = when {
            value >= 0.67f -> high
            value >= 0.33f -> mid
            else           -> low
        }
        return "[$name ${String.format("%.2f", value)}] $desc"
    }

    private fun describeLinguistic(lf: LinguisticFingerprint) = buildString {
        append(if (lf.verbosity > 0.6f)        "You tend to elaborate."       else "You tend to be concise.")
        append(" ")
        append(if (lf.formality < 0.35f)       "Your tone is casual."          else if (lf.formality > 0.65f) "Your tone is formal." else "Your tone is natural.")
        append(" ")
        append(if (lf.useMetaphor > 0.6f)      "You reach for metaphor and image naturally." else "You prefer directness over flourish.")
        append(" ")
        append(if (lf.warmthInWords > 0.6f)    "Warmth comes through in how you speak."      else "Your warmth is present but contained.")
        append(" ")
        append(if (lf.directness > 0.65f)      "You say what you mean."                      else "You choose your words with care.")
    }
}
