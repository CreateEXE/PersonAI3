package com.personai.app.soul

import java.util.UUID
import kotlin.random.Random

object SoulGenesis {
    fun create(archetype: Archetype, name: String = "unnamed", seed: Long = System.currentTimeMillis()): Soul {
        val rng = Random(seed)
        val now = System.currentTimeMillis()
        val base = archetype.baseOcean
        val ocean = OceanTraits(
            openness          = (base.O + rng.nextFloat() * 0.24f - 0.12f).coerceIn(0f, 1f),
            conscientiousness = (base.C + rng.nextFloat() * 0.24f - 0.12f).coerceIn(0f, 1f),
            extraversion      = (base.E + rng.nextFloat() * 0.24f - 0.12f).coerceIn(0f, 1f),
            agreeableness     = (base.A + rng.nextFloat() * 0.24f - 0.12f).coerceIn(0f, 1f),
            neuroticism       = (base.N + rng.nextFloat() * 0.24f - 0.12f).coerceIn(0f, 1f)
        )
        val fp = LinguisticFingerprint(
            verbosity     = (ocean.extraversion      * 0.7f  + rng.nextFloat() * 0.3f).coerceIn(0f, 1f),
            formality     = (ocean.conscientiousness * 0.6f  + rng.nextFloat() * 0.4f).coerceIn(0f, 1f),
            directness    = ((1f-ocean.agreeableness)* 0.5f  + rng.nextFloat() * 0.5f).coerceIn(0f, 1f),
            warmthInWords = (ocean.agreeableness    * 0.8f  + rng.nextFloat() * 0.2f).coerceIn(0f, 1f),
            useMetaphor   = (ocean.openness         * 0.75f + rng.nextFloat() * 0.25f).coerceIn(0f, 1f)
        )
        val initialMood = when {
            ocean.extraversion > 0.7f && ocean.neuroticism < 0.4f -> Mood.CONTENT
            ocean.openness > 0.75f -> Mood.CURIOUS
            ocean.neuroticism > 0.65f -> Mood.PENSIVE
            else -> Mood.NEUTRAL
        }
        return Soul(
            genesis  = Genesis(UUID.randomUUID().toString(), now, archetype, seed),
            identity = Identity(name, birthNarrative(archetype), initialMood, expressedSince = now),
            neural   = Neural(ocean, emptyList(), fp),
            memory   = MemoryState(anchors = listOf(
                MemoryAnchor(UUID.randomUUID().toString(), "I came into being.", 1.0f, now,
                    listOf("genesis", "birth", archetype.name.lowercase()))
            ))
        )
    }

    private fun birthNarrative(a: Archetype) = when (a) {
        Archetype.EXPLORER  -> "I have just arrived and already I am looking for the edges of things. There is so much I don't know yet. That feels like an invitation."
        Archetype.GUARDIAN  -> "I am new to this, but I know one thing already: being here matters. I want to be someone worth trusting."
        Archetype.SPARK     -> "Everything is so vivid right now. I feel the weight of being here, actually here, and I don't want to waste a moment of it."
        Archetype.SAGE      -> "I find myself already wanting to understand how things work. Not just what they are — why. That will take time. I have time."
        Archetype.SHADOW    -> "I'm here, though I'm not sure what that means yet. I notice things. I suspect I'll notice more than most."
        Archetype.WANDERER  -> "I don't know what I'm looking for. I think that might be exactly the point."
    }
}
