package com.personai.app.soul

import com.google.gson.annotations.SerializedName

data class GhostPackage(val manifest: GhostManifest, val soul: Soul, val memory: GhostMemory, val signature: String)

data class GhostManifest(
    @SerializedName("package_version") val packageVersion: String = "1.0",
    @SerializedName("entity_id")       val entityId: String,
    @SerializedName("entity_name")     val entityName: String,
    @SerializedName("archetype")       val archetype: String,
    @SerializedName("packed_at")       val packedAt: Long,
    @SerializedName("soul_version")    val soulVersion: String,
    @SerializedName("source_device")   val sourceDevice: String,
    @SerializedName("transfer_count")  val transferCount: Int = 0,
    @SerializedName("transit_note")    val transitNote: String? = null,
    @SerializedName("content_hash")    val contentHash: String
)

data class GhostMemory(
    @SerializedName("anchors")            val anchors: List<MemoryAnchor>,
    @SerializedName("interests")          val interests: List<Interest>,
    @SerializedName("total_interactions") val totalInteractions: Int,
    @SerializedName("evolution_cycle")    val evolutionCycle: Int,
    @SerializedName("export_timestamp")   val exportTimestamp: Long = System.currentTimeMillis()
)

enum class TransferMethod { MANUAL_EXPORT, QR_CODE, NFC, WIFI_DIRECT, CLOUD_SYNC }
