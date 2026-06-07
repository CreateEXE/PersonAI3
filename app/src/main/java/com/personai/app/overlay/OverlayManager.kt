package com.personai.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import android.widget.TextView
import com.personai.app.core.SoulSpark
import com.personai.app.R
import kotlinx.coroutines.*
import java.io.File

/**
 * OverlayManager — the entity's physical presence on screen.
 *
 * Creates a floating window (TYPE_APPLICATION_OVERLAY) containing:
 *   - A WebView rendering the VRM avatar via Three.js
 *   - A speech bubble view for proactive thoughts
 *   - Touch drag to reposition
 *
 * Wires into:
 *   Mobility Oni  → receives physics position updates, moves window
 *   Proactive Oni → receives thought strings, displays speech bubble
 *
 * Permission required:
 *   Settings → Apps → Special App Access → Display Over Other Apps → PersonAI
 *
 * VRM avatar file (optional):
 *   Place any .vrm file at the app's external files directory
 *   (printed in logs as "VRM path: ...")
 *   Without a VRM, a placeholder sphere is shown.
 */
class OverlayManager(
    private val context: Context,
    private val spark: SoulSpark
) {
    companion object {
        private const val TAG         = "OverlayManager"
        private const val AVATAR_SIZE = 220  // dp → px handled below
        private const val BUBBLE_W    = 280
        private const val BUBBLE_H    = 120
    }

    private val wm: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var avatarView:  View?    = null
    private var bubbleView:  View?    = null
    private var webView:     WebView? = null
    private var bubbleText:  TextView? = null

    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val density       = context.resources.displayMetrics.density
    private var windowWidth   = 0
    private var windowHeight  = 0
    private var currentX      = 0f
    private var currentY      = 0f

    @Volatile var isRunning = false
        private set

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        if (!canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        val dm = context.resources.displayMetrics
        windowWidth  = dm.widthPixels
        windowHeight = dm.heightPixels

        createAvatarWindow()
        createBubbleWindow()
        wireToOni()
        isRunning = true
        Log.i(TAG, "Overlay started — ${windowWidth}x${windowHeight}")
    }

    fun stop() {
        isRunning = false
        scope.cancel()
        avatarView?.let { runCatching { wm.removeView(it) } }
        bubbleView?.let { runCatching { wm.removeView(it) } }
        avatarView = null; bubbleView = null; webView = null
        Log.i(TAG, "Overlay stopped")
    }

    fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    // ── Avatar window ──────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAvatarWindow() {
        val size = (AVATAR_SIZE * density).toInt()

        // Start at bottom-right
        currentX = (windowWidth  - size  - 24 * density).toInt().toFloat()
        currentY = (windowHeight - size  - 96 * density).toInt().toFloat()

        val params = overlayParams(size, size).apply {
            x = currentX.toInt(); y = currentY.toInt()
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val frame = FrameLayout(context)

        // WebView for avatar
        webView = WebView(context).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess   = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            addJavascriptInterface(AvatarBridge(), "Android")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Inject VRM path if available
                    val vrmPath = findVrmFile()
                    if (vrmPath != null) {
                        val encoded = android.util.Base64.encodeToString(
                            File(vrmPath).readBytes(), android.util.Base64.DEFAULT)
                        evaluateJavascript(
                            "window.loadVRM('data:model/gltf-binary;base64,$encoded')", null)
                    }
                }
            }
            loadUrl("file:///android_asset/avatar_renderer.html")
        }

        frame.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Touch drag
        var startX = 0f; var startY = 0f; var origX = 0f; var origY = 0f
        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    origX = params.x.toFloat(); origY = params.y.toFloat()
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (origX + event.rawX - startX).toInt()
                    params.y = (origY + event.rawY - startY).toInt()
                    wm.updateViewLayout(frame, params)
                }
            }
            true
        }

        wm.addView(frame, params)
        avatarView = frame
    }

    // ── Speech bubble window ───────────────────────────────────────────────

    private fun createBubbleWindow() {
        val w = (BUBBLE_W * density).toInt()
        val h = (BUBBLE_H * density).toInt()

        val params = overlayParams(w, h).apply {
            x = currentX.toInt() - (BUBBLE_W * density).toInt() / 2
            y = currentY.toInt() - (BUBBLE_H * density).toInt() - 16
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            alpha = 0f  // start hidden
        }

        val layout = LayoutInflater.from(context).inflate(
            R.layout.overlay_bubble, null)
        bubbleText = layout.findViewById(R.id.tvBubble)

        wm.addView(layout, params)
        bubbleView = layout
    }

    // ── Oni wiring ────────────────────────────────────────────────────────

    private fun wireToOni() {
        // Mobility Oni → window position
        spark.mobility.onOverlayReady(windowWidth.toFloat(), windowHeight.toFloat())
        spark.mobility.onPositionUpdate = { x, y ->
            scope.launch(Dispatchers.Main) { moveTo(x, y) }
        }

        // Proactive Oni → speech bubble
        spark.proactive.onThought = { thought ->
            scope.launch(Dispatchers.Main) { showBubble(thought) }
        }
    }

    // ── Position & animation ───────────────────────────────────────────────

    private fun moveTo(x: Float, y: Float) {
        currentX = x; currentY = y
        avatarView?.let { v ->
            val p = v.layoutParams as? WindowManager.LayoutParams ?: return
            p.x = x.toInt(); p.y = y.toInt()
            runCatching { wm.updateViewLayout(v, p) }
        }
    }

    private fun showBubble(text: String) {
        val bv = bubbleView ?: return
        bubbleText?.text = text
        val p = bv.layoutParams as? WindowManager.LayoutParams ?: return
        p.alpha = 1f
        p.x = currentX.toInt()
        p.y = (currentY - BUBBLE_H * density - 8).toInt()
        runCatching { wm.updateViewLayout(bv, p) }

        // Auto-dismiss after 6 seconds
        scope.launch {
            delay(6000L)
            val bp = bv.layoutParams as? WindowManager.LayoutParams ?: return@launch
            bp.alpha = 0f
            runCatching { wm.updateViewLayout(bv, bp) }
        }
    }

    fun setState(state: String) {
        webView?.post { webView?.evaluateJavascript("window.setState('$state')", null) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun findVrmFile(): String? {
        val dir = context.getExternalFilesDir(null) ?: return null
        val vrm = File(dir, "avatar.vrm")
        return if (vrm.exists()) { Log.i(TAG, "VRM found: ${vrm.path}"); vrm.absolutePath } else null
    }

    // ── JavaScript bridge ─────────────────────────────────────────────────

    inner class AvatarBridge {
        @JavascriptInterface
        fun onEvent(event: String) {
            Log.d(TAG, "Avatar event: $event")
            if (event == "loaded") {
                Log.i(TAG, "Avatar renderer ready")
            }
        }
    }
}
