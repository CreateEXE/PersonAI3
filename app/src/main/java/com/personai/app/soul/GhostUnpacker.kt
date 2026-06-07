package com.personai.app.soul

import android.util.Log; import com.google.gson.Gson
import java.io.File; import java.util.zip.ZipFile; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec

object GhostUnpacker {
    private const val TAG = "GhostUnpacker"; private val gson = Gson()

    sealed class Result {
        data class Success(val pkg: GhostPackage) : Result()
        data class Failure(val reason: String)    : Result()
    }

    fun unpack(ghostFile: File): Result {
        if (!ghostFile.exists() || !ghostFile.name.endsWith(".ghost"))
            return Result.Failure("Invalid ghost file")
        return try {
            val e    = entries(ghostFile)
            val mfJ  = e["manifest.json"] ?: return Result.Failure("Missing manifest")
            val sJ   = e["soul.json"]     ?: return Result.Failure("Missing soul")
            val memJ = e["memory.json"]   ?: return Result.Failure("Missing memory")
            val sig  = e["SIG"]           ?: return Result.Failure("Missing signature")
            val mf   = gson.fromJson(mfJ, GhostManifest::class.java)
            if (!sign(mfJ+sJ+memJ, mf.entityId).equals(sig.trim(), ignoreCase=true))
                return Result.Failure("Signature mismatch")
            if (sha256("$sJ|$memJ") != mf.contentHash) return Result.Failure("Hash mismatch")
            val soul = gson.fromJson(sJ, Soul::class.java)
            val mem  = gson.fromJson(memJ, GhostMemory::class.java)
            val merged = soul.copy(
                memory = soul.memory.copy(anchors=mem.anchors, episodeCount=mem.totalInteractions),
                neural = soul.neural.copy(interests=mem.interests),
                meta   = soul.meta.copy(totalInteractions=mem.totalInteractions, evolutionCycle=mem.evolutionCycle)
            )
            Log.i(TAG, "Unpacked: ${mf.entityName}"); Result.Success(GhostPackage(mf, merged, mem, sig))
        } catch (e: Exception) { Result.Failure("Error: ${e.message}") }
    }

    private fun entries(f: File): Map<String, String> {
        val m = mutableMapOf<String, String>()
        ZipFile(f).use { z -> z.entries().asSequence().forEach { m[it.name] = z.getInputStream(it).bufferedReader(Charsets.UTF_8).readText() } }
        return m
    }
    private fun sha256(s: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sign(content: String, entityId: String): String {
        val key = sha256(entityId).take(32).toByteArray()
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
