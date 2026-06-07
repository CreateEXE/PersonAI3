package com.personai.app.soul

import android.util.Log
import com.google.gson.Gson

/**
 * SoulMigration — ensures old soul documents load correctly
 * even after Soul.kt has evolved. Add a new case here every time
 * the soul schema changes in a breaking way.
 */
object SoulMigration {

    private const val TAG = "SoulMigration"
    private val gson = Gson()

    fun needsMigration(soul: Soul): Boolean =
        soul.meta.schemaVersion != Soul.SOUL_VERSION

    /**
     * Migrate a raw soul JSON string from an older schema version.
     * Returns the migrated Soul, or null if migration is impossible.
     */
    fun migrate(json: String, fromVersion: String): Soul? {
        Log.i(TAG, "Migrating soul from schema $fromVersion → ${Soul.SOUL_VERSION}")
        return try {
            when (fromVersion) {
                "1.0" -> migrateFrom1_0(json)
                else  -> { Log.e(TAG, "Unknown schema version: $fromVersion"); null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed from $fromVersion: ${e.message}"); null
        }
    }

    // 1.0 → current: no structural changes yet, bump schema version
    private fun migrateFrom1_0(json: String): Soul {
        val soul = gson.fromJson(json, Soul::class.java)
        return soul.copy(meta = soul.meta.copy(schemaVersion = Soul.SOUL_VERSION))
    }
}
