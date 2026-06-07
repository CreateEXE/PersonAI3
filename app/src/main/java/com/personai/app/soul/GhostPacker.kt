package com.personai.app.soul

import android.content.Context; import android.os.Build; import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File; import java.util.zip.*; import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec

object GhostPacker {
    private const val TAG = "GhostPacker"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun pack(soul: Soul, context: Context, outputDir: File, transitNote: String? = null): File {
        val ts       = System.currentTimeMillis()
        val safeName = soul.identity.name.replace(" ","_").replace(Regex("[^a-zA-Z0-9_]"),"").lowercase().ifEmpty{"entity"}
        val outFile  = File(outputDir, "${safeName}_${ts}.ghost")
        val mem      = GhostMemory(soul.memory.anchors, soul.neural.interests, soul.meta.totalInteractions, soul.meta.evolutionCycle)
        val sJ       = gson.toJson(soul); val mJ = gson.toJson(mem)
        val hash     = sha256("$sJ|$mJ")
        val manifest = GhostManifest(entityId=soul.genesis.id, entityName=soul.identity.name,
            archetype=soul.genesis.archetype.name, packedAt=ts, soulVersion=soul.version,
            sourceDevice="${Build.MANUFACTURER}_${Build.MODEL}".replace(" ","_"),
            transitNote=transitNote, contentHash=hash)
        val mfJ = gson.toJson(manifest)
        val sig = sign(mfJ + sJ + mJ, soul.genesis.id)
        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            listOf("manifest.json" to mfJ, "soul.json" to sJ, "memory.json" to mJ, "SIG" to sig)
                .forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
        }
        Log.i(TAG, "Packed ${outFile.length()} bytes → ${outFile.name}"); return outFile
    }

    fun packToCache(soul: Soul, context: Context): File {
        val dir = File(context.filesDir, "ghost_cache").also { it.mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
        return pack(soul, context, dir)
    }

    private fun sha256(s: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sign(content: String, entityId: String): String {
        val key = sha256(entityId).take(32).toByteArray()
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
