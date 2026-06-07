package com.personai.app.core

import android.util.Log
import com.personai.app.soul.*
import kotlinx.coroutines.*

/**
 * Mobility — avatar movement and physical presence manager.
 *
 * Controls the entity's position on screen via spring physics.
 * Movement patterns are personality-driven:
 *   High E  — moves more frequently, drawn to center
 *   Low C   — erratic movement, doesn't settle
 *   High N  — mood affects preferred screen zones
 *   High O  — explores all areas of the screen
 *
 * Currently a stub — wired to OverlayManager when the avatar layer is built.
 * The physics model and personality mapping are defined here
 * so the overlay layer can call in without knowing the soul directly.
 */
class Mobility(spark: SoulSpark) : Oni(spark) {

    override val id = "mobility"

    // Spring physics state
    private var posX = 0f; private var posY = 0f
    private var velX = 0f; private var velY = 0f
    private var targetX = 0f; private var targetY = 0f

    // Screen dimensions — set by OverlayManager when initialized
    var screenWidth  = 1080f
    var screenHeight = 1920f

    // Callback — registered by OverlayManager to receive position updates
    var onPositionUpdate: ((x: Float, y: Float) -> Unit)? = null

    override fun wake() {
        // Stays IDLE until OverlayManager connects
        reportHealth(OniHealth.IDLE)
        Log.i(id, "Stub online — awaiting OverlayManager")
    }

    /**
     * Called by OverlayManager once the window is ready.
     * Starts the physics tick loop.
     */
    fun onOverlayReady(screenW: Float, screenH: Float) {
        screenWidth = screenW; screenHeight = screenH
        posX = screenW * 0.8f; posY = screenH * 0.7f
        reportHealth(OniHealth.RUNNING)
        scope.launch { physicsLoop() }
        Log.i(id, "Physics loop started — ${screenW.toInt()}x${screenH.toInt()}")
    }

    // ── Physics ───────────────────────────────────────────────────────────

    private suspend fun physicsLoop() {
        var lastWander = 0L
        while (scope.isActive) {
            delay(TICK_MS)
            val soul = soul ?: continue
            val now  = System.currentTimeMillis()

            // Wander — pick new target periodically
            if (now - lastWander > wanderInterval(soul)) {
                setRandomTarget(soul)
                lastWander = now
            }

            // Spring toward target
            val springK  = 0.08f
            val damping  = 0.75f
            val forceX   = (targetX - posX) * springK
            val forceY   = (targetY - posY) * springK
            velX = (velX + forceX) * damping
            velY = (velY + forceY) * damping
            posX = (posX + velX).coerceIn(0f, screenWidth  - AVATAR_SIZE)
            posY = (posY + velY).coerceIn(0f, screenHeight - AVATAR_SIZE)

            onPositionUpdate?.invoke(posX, posY)
        }
    }

    private fun setRandomTarget(soul: Soul) {
        val o = soul.neural.ocean
        // High O explores widely; low O stays near edges
        val marginX = if (o.openness > 0.6f) 0.1f else 0.3f
        val marginY = if (o.openness > 0.6f) 0.1f else 0.3f

        // Mood influences preferred zone
        val yBias = when (soul.identity.currentMood) {
            Mood.MELANCHOLIC -> 0.75f  // bottom of screen
            Mood.ELATED      -> 0.25f  // top of screen
            else             -> 0.5f   // anywhere
        }

        targetX = screenWidth  * (marginX + Math.random().toFloat() * (1f - marginX * 2))
        targetY = screenHeight * (yBias - 0.2f + Math.random().toFloat() * 0.4f).coerceIn(marginY, 1f - marginY)
    }

    private fun wanderInterval(soul: Soul): Long {
        // High E moves more; high N is more irregular
        val base    = 10_000L
        val eAdjust = ((1f - soul.neural.ocean.extraversion) * 15_000L).toLong()
        val nNoise  = (Math.random() * soul.neural.ocean.neuroticism * 5_000L).toLong()
        return (base + eAdjust + nNoise).coerceIn(3_000L, 30_000L)
    }

    companion object {
        private const val TICK_MS    = 16L    // ~60fps
        private const val AVATAR_SIZE = 200f
    }
}
