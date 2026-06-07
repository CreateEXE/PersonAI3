package com.personai.app.soul

import com.google.gson.annotations.SerializedName

data class Soul(
    @SerializedName("version")  val version: String = SOUL_VERSION,
    @SerializedName("genesis")  val genesis: Genesis,
    @SerializedName("identity") val identity: Identity = Identity(),
    @SerializedName("neural")   val neural: Neural,
    @SerializedName("memory")   val memory: MemoryState = MemoryState(),
    @SerializedName("meta")     val meta: Meta = Meta()
) {
    companion object { const val SOUL_VERSION = "1.0" }
}

data class Genesis(
    @SerializedName("id")               val id: String,
    @SerializedName("birth_timestamp")  val birthTimestamp: Long,
    @SerializedName("archetype")        val archetype: Archetype,
    @SerializedName("seed")             val seed: Long
)

enum class Archetype(val displayName: String, val description: String, val baseOcean: OceanBase) {
    EXPLORER("Explorer", "Driven by curiosity.",          OceanBase(O=0.82f,C=0.42f,E=0.65f,A=0.55f,N=0.38f)),
    GUARDIAN("Guardian", "Finds meaning in care.",        OceanBase(O=0.52f,C=0.85f,E=0.50f,A=0.88f,N=0.30f)),
    SPARK   ("Spark",    "Intense, feels everything.",    OceanBase(O=0.72f,C=0.38f,E=0.90f,A=0.60f,N=0.75f)),
    SAGE    ("Sage",     "Patient and systematic.",       OceanBase(O=0.88f,C=0.80f,E=0.35f,A=0.65f,N=0.25f)),
    SHADOW  ("Shadow",   "Complicated inner world.",      OceanBase(O=0.75f,C=0.45f,E=0.28f,A=0.35f,N=0.80f)),
    WANDERER("Wanderer", "Drawn to the new and unknown.", OceanBase(O=0.90f,C=0.22f,E=0.58f,A=0.62f,N=0.48f))
}

data class OceanBase(val O: Float, val C: Float, val E: Float, val A: Float, val N: Float)

data class Identity(
    @SerializedName("name")            val name: String = "unnamed",
    @SerializedName("self_narrative")  val selfNarrative: String = "I have just come into being.",
    @SerializedName("current_mood")    val currentMood: Mood = Mood.NEUTRAL,
    @SerializedName("obsession")       val obsession: String? = null,
    @SerializedName("expressed_since") val expressedSince: Long = System.currentTimeMillis()
)

enum class Mood(val display: String, val valence: Float) {
    ELATED("elated", 1.0f), CONTENT("content", 0.6f), CURIOUS("curious", 0.3f),
    NEUTRAL("neutral", 0.0f), PENSIVE("pensive", -0.2f),
    UNSETTLED("unsettled", -0.5f), MELANCHOLIC("melancholic", -0.8f)
}

data class Neural(
    @SerializedName("ocean")                  val ocean: OceanTraits,
    @SerializedName("interests")              val interests: List<Interest> = emptyList(),
    @SerializedName("linguistic_fingerprint") val linguisticFingerprint: LinguisticFingerprint = LinguisticFingerprint()
)

data class OceanTraits(
    @SerializedName("openness")           val openness: Float,
    @SerializedName("conscientiousness")  val conscientiousness: Float,
    @SerializedName("extraversion")       val extraversion: Float,
    @SerializedName("agreeableness")      val agreeableness: Float,
    @SerializedName("neuroticism")        val neuroticism: Float
) {
    fun drift(trait: String, delta: Float): OceanTraits = when (trait) {
        "O" -> copy(openness          = (openness          + delta).coerceIn(0f, 1f))
        "C" -> copy(conscientiousness = (conscientiousness + delta).coerceIn(0f, 1f))
        "E" -> copy(extraversion      = (extraversion      + delta).coerceIn(0f, 1f))
        "A" -> copy(agreeableness     = (agreeableness     + delta).coerceIn(0f, 1f))
        "N" -> copy(neuroticism       = (neuroticism       + delta).coerceIn(0f, 1f))
        else -> this
    }
}

data class Interest(
    @SerializedName("topic")         val topic: String,
    @SerializedName("weight")        val weight: Float,
    @SerializedName("last_engaged")  val lastEngaged: Long,
    @SerializedName("origin")        val origin: String = "organic"
)

data class LinguisticFingerprint(
    @SerializedName("verbosity")       val verbosity: Float = 0.5f,
    @SerializedName("formality")       val formality: Float = 0.4f,
    @SerializedName("directness")      val directness: Float = 0.6f,
    @SerializedName("warmth_in_words") val warmthInWords: Float = 0.5f,
    @SerializedName("use_metaphor")    val useMetaphor: Float = 0.5f
)

data class MemoryState(
    @SerializedName("episode_count")    val episodeCount: Int = 0,
    @SerializedName("last_interaction") val lastInteraction: Long? = null,
    @SerializedName("anchors")          val anchors: List<MemoryAnchor> = emptyList()
)

data class MemoryAnchor(
    @SerializedName("id")               val id: String,
    @SerializedName("summary")          val summary: String,
    @SerializedName("emotional_weight") val emotionalWeight: Float,
    @SerializedName("timestamp")        val timestamp: Long,
    @SerializedName("tags")             val tags: List<String> = emptyList()
)

data class Meta(
    @SerializedName("evolution_cycle")    val evolutionCycle: Int = 0,
    @SerializedName("total_interactions") val totalInteractions: Int = 0,
    @SerializedName("schema_version")     val schemaVersion: String = Soul.SOUL_VERSION,
    @SerializedName("checksum")           val checksum: String = ""
)
