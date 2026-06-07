package com.personai.app.core

/**
 * InferenceParams — sampling configuration for a single inference call.
 * Oni hooks receive these and return modified versions before every inference.
 */
data class InferenceParams(
    val temperature:   Float = 0.8f,
    val topP:          Float = 0.9f,
    val topK:          Int   = 40,
    val repeatPenalty: Float = 1.1f,
    val repeatLastN:   Int   = 64,
    val maxTokens:     Int   = 256,
    val logitBias:     Map<Int, Float> = emptyMap()
) {
    companion object {
        val CONVERSATIONAL = InferenceParams(temperature=0.8f,  topP=0.90f, topK=40, maxTokens=256)
        val REFLECTIVE     = InferenceParams(temperature=0.7f,  topP=0.85f, topK=30, maxTokens=512)
        val CREATIVE       = InferenceParams(temperature=1.1f,  topP=0.95f, topK=60, maxTokens=384)
        val PRECISE        = InferenceParams(temperature=0.4f,  topP=0.80f, topK=20, maxTokens=128)
        val DREAM          = InferenceParams(temperature=1.2f,  topP=0.98f, topK=80, maxTokens=1024)
    }
}

/**
 * OniHook — implemented by any Oni that wants to shape inference parameters.
 * SoulSpark chains all registered hooks before submitting to InferenceQueue.
 */
interface OniHook {
    fun apply(params: InferenceParams, soul: com.personai.app.soul.Soul): InferenceParams
}
