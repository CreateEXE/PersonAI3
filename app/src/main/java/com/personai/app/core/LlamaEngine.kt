package com.personai.app.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * LlamaEngine — llama.cpp JNI bridge.
 *
 * Handles model loading, inference, and KV cache state save/load.
 * Wired into InferenceQueue as the executor by SoulSpark.
 *
 * Target hardware: Revvl 7, Snapdragon 6 Gen 1, CPU only.
 * Recommended model: Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
 *
 * Model must be placed at:
 *   /sdcard/Android/data/com.personai.app/files/models/model.gguf
 * (or the path returned by defaultModelPath())
 */
class LlamaEngine(private val context: Context) {

    companion object {
        private const val TAG              = "LlamaEngine"
        private const val DEFAULT_CTX      = 2048
        private const val DEFAULT_THREADS  = 4  // leave 2 cores free for OS

        init {
            try {
                System.loadLibrary("personai-jni")
                Log.i(TAG, "JNI library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "JNI load failed: ${e.message}")
            }
        }
    }

    @Volatile private var handle: Long = 0L
    @Volatile var isLoaded: Boolean    = false
        private set

    // ── JNI declarations ──────────────────────────────────────────────────

    private external fun nativeInit(modelPath: String, contextSize: Int, threads: Int, gpuLayers: Int): Long
    private external fun nativeInfer(handle: Long, prompt: String, temp: Float, topP: Float, topK: Int, maxTokens: Int, repeatPenalty: Float): String
    private external fun nativeSaveState(handle: Long, filePath: String): Boolean
    private external fun nativeLoadState(handle: Long, filePath: String): Boolean
    private external fun nativeFree(handle: Long)

    // ── Lifecycle ─────────────────────────────────────────────────────────

    suspend fun loadModel(
        modelPath: String = defaultModelPath(),
        contextSize: Int  = DEFAULT_CTX,
        threads: Int      = DEFAULT_THREADS,
        gpuLayers: Int    = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext true
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model not found: $modelPath"); return@withContext false
        }
        Log.i(TAG, "Loading: $modelPath")
        val h = nativeInit(modelPath, contextSize, threads, gpuLayers)
        if (h == 0L) { Log.e(TAG, "Init returned null handle"); return@withContext false }
        handle = h; isLoaded = true
        Log.i(TAG, "Model loaded"); true
    }

    fun free() {
        if (handle != 0L) { nativeFree(handle); handle = 0L; isLoaded = false }
    }

    // ── Inference (called by InferenceQueue executor) ─────────────────────

    suspend fun infer(request: InferenceQueue.Request): String = withContext(Dispatchers.IO) {
        if (!isLoaded || handle == 0L) return@withContext "[model not loaded]"
        val p = request.params
        try {
            nativeInfer(handle, request.prompt, p.temperature, p.topP, p.topK, p.maxTokens, p.repeatPenalty)
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "Infer error: ${e.message}"); "[error: ${e.message}]"
        }
    }

    // ── KV cache state (for soul context persistence) ─────────────────────

    suspend fun saveState(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded) return@withContext false
        nativeSaveState(handle, path)
    }

    suspend fun loadState(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLoaded || !File(path).exists()) return@withContext false
        nativeLoadState(handle, path)
    }

    // ── Paths ─────────────────────────────────────────────────────────────

    /** Default model path — external app files dir for easy access. */
    fun defaultModelPath(): String =
        File(context.getExternalFilesDir(null), "models/model.gguf").absolutePath

    fun modelExists(): Boolean = File(defaultModelPath()).exists()
}
