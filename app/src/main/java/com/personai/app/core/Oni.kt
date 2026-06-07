package com.personai.app.core

import com.personai.app.soul.Soul
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Oni — base class for all PersonAI subsystems.
 *
 * Each Oni has a single purpose, a health state, and reports back to SoulSpark.
 * Named by what they do. Classified as Oni.
 *
 * Oni that influence inference implement OniHook on top of this.
 */
abstract class Oni(protected val spark: SoulSpark) {

    /** Unique identifier — used by SoulSpark's registry and health monitor. */
    abstract val id: String

    /** Current operational health. */
    var health: OniHealth = OniHealth.IDLE
        protected set

    /** Coroutine scope for this Oni's background work. */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bring this Oni online. */
    abstract fun wake()

    /** Take this Oni offline cleanly. */
    open fun sleep() {
        scope.cancel()
        reportHealth(OniHealth.IDLE)
    }

    /**
     * Report health status to SoulSpark.
     * SoulSpark restarts any Oni that reports DEAD.
     */
    protected fun reportHealth(status: OniHealth) {
        health = status
        spark.onOniHealthReport(id, status)
    }

    /** Convenience — current soul state. */
    protected val soul: Soul? get() = spark.soul
}

enum class OniHealth {
    RUNNING,    // operating normally
    IDLE,       // standing by, not processing
    DEGRADED,   // running but with errors
    DEAD        // crashed — SoulSpark will restart
}
