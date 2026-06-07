package com.personai.app.soul

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.security.MessageDigest

/**
 * SoulEngine — persistence layer. Managed by SoulSpark.
 * All soul writes route through SoulSpark, which calls here.
 */
class SoulEngine(private val context: Context) {

    companion object {
        private const val TAG         = "SoulEngine"
        private const val SOUL_FILE   = "soul.json"
        private const val SOUL_BACKUP = "soul.bak.json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson  = GsonBuilder().setPrettyPrinting().create()

    private val _state = MutableStateFlow<SoulState>(SoulState.Uninitialized)
    val soulState: StateFlow<SoulState> = _state.asStateFlow()
    val soul: Soul? get() = (_state.value as? SoulState.Alive)?.soul

    sealed class SoulState {
        object Uninitialized              : SoulState()
        object Loading                    : SoulState()
        data class Alive(val soul: Soul)  : SoulState()
        data class Error(val msg: String) : SoulState()
    }

    fun wake() {
        scope.launch {
            _state.value = SoulState.Loading
            val s = loadFromDisk()
            _state.value = if (s != null) { Log.i(TAG, "Awakened: ${s.genesis.id}"); SoulState.Alive(s) }
                           else { Log.i(TAG, "No soul — awaiting genesis"); SoulState.Uninitialized }
        }
    }

    suspend fun genesis(archetype: Archetype, name: String = "unnamed"): Soul = withContext(Dispatchers.IO) {
        val s = SoulGenesis.create(archetype, name)
        save(s); _state.value = SoulState.Alive(s)
        Log.i(TAG, "Genesis complete: ${s.genesis.id}"); s
    }

    suspend fun mutate(block: Soul.() -> Soul) = withContext(Dispatchers.IO) {
        val current = soul ?: return@withContext
        val mutated = current.block()
        val stamped = mutated.copy(meta = mutated.meta.copy(checksum = checksum(mutated)))
        save(stamped); _state.value = SoulState.Alive(stamped)
    }

    suspend fun driftTrait(trait: String, delta: Float) =
        mutate { copy(neural = neural.copy(ocean = neural.ocean.drift(trait, delta))) }
    suspend fun setMood(mood: Mood) =
        mutate { copy(identity = identity.copy(currentMood = mood)) }
    suspend fun setObsession(topic: String?) =
        mutate { copy(identity = identity.copy(obsession = topic)) }
    suspend fun recordInteraction() = mutate {
        copy(memory = memory.copy(lastInteraction = System.currentTimeMillis()),
             meta   = meta.copy(totalInteractions = meta.totalInteractions + 1))
    }

    fun emergencySave() { soul?.let { save(it) } }

    private fun save(soul: Soul) {
        try {
            val json = gson.toJson(soul)
            val file = File(context.filesDir, SOUL_FILE)
            if (file.exists()) file.copyTo(File(context.filesDir, SOUL_BACKUP), overwrite = true)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) { Log.e(TAG, "Save failed: ${e.message}") }
    }

    private fun loadFromDisk(): Soul? = try {
        val f = File(context.filesDir, SOUL_FILE)
        if (!f.exists()) null else gson.fromJson(f.readText(Charsets.UTF_8), Soul::class.java)
    } catch (e: Exception) { Log.e(TAG, "Load failed, trying backup"); tryBackup() }

    private fun tryBackup(): Soul? = try {
        val f = File(context.filesDir, SOUL_BACKUP)
        if (!f.exists()) null else gson.fromJson(f.readText(Charsets.UTF_8), Soul::class.java)
    } catch (e: Exception) { null }

    private fun checksum(soul: Soul): String {
        val content = "${soul.genesis.id}:${soul.meta.totalInteractions}:${soul.neural.ocean}"
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
    }
}
