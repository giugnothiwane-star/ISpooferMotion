package com.chessoverlay.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chessoverlay.analysis.AnalysisSettings
import com.chessoverlay.analysis.PositionAnalysis
import com.chessoverlay.app.MainActivity
import com.chessoverlay.app.R
import com.chessoverlay.app.container
import com.chessoverlay.app.util.DiagnosticLog
import com.chessoverlay.app.vision.RecognitionSession
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.ui.theme.ChessOverlayTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** État affiché dans le panneau flottant. */
sealed interface OverlayPhase {
    data object Idle : OverlayPhase
    data class Working(val text: String) : OverlayPhase
    data class Recognized(val session: RecognitionSession, val side: PieceColor) : OverlayPhase
    data class Analyzing(val session: RecognitionSession, val side: PieceColor, val analysis: PositionAnalysis?, val running: Boolean) : OverlayPhase
    /** [canFrame] : la capture est disponible pour un cadrage manuel dans l'application. */
    data class Error(val message: String, val canFrame: Boolean = false) : OverlayPhase
}

data class OverlayState(val expanded: Boolean = false, val phase: OverlayPhase = OverlayPhase.Idle)

/**
 * Service de premier plan qui affiche la bulle et le panneau au-dessus des autres applications
 * (autorisation officielle « Affichage par-dessus d'autres applis »).
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    private var bubble: ComposeView? = null
    private var panel: ComposeView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var capturer: ScreenCapturer
    private var analysisJob: Job? = null
    private var captureAfterGrant = false

    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        instance = this
        wm = getSystemService(WindowManager::class.java)
        capturer = ScreenCapturer(this) { lifecycleScope.launch { updateForeground(projection = false) } }
        updateForeground(projection = false)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addBubble()
        _running.value = true
        // Applique la transparence et la taille choisies dans les réglages.
        lifecycleScope.launch {
            container.settings.collect { s ->
                panel?.let { v ->
                    panelParams.alpha = s.overlayAlpha
                    runCatching { wm.updateViewLayout(v, panelParams) }
                }
                bubble?.let { v ->
                    bubbleParams.alpha = (s.overlayAlpha + 0.1f).coerceAtMost(1f)
                    runCatching { wm.updateViewLayout(v, bubbleParams) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_GRANTED -> {
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null) {
                    try {
                        updateForeground(projection = true)
                        capturer.start(code, data)
                        if (captureAfterGrant) { captureAfterGrant = false; capture() }
                    } catch (e: Exception) {
                        val why = DiagnosticLog.record("autorisation de capture", e)
                        runCatching { updateForeground(projection = false) }
                        setPhase(OverlayPhase.Error("Android a refusé de démarrer la capture d'écran ($why). Réessayez ; si le problème persiste, copiez le journal de diagnostic depuis Réglages."))
                        expand(true)
                    }
                }
            }
            ACTION_DENIED -> { captureAfterGrant = false; setPhase(OverlayPhase.Error("Capture d'écran refusée. Vous pouvez réessayer à tout moment.")); expand(true) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    // ------------------------------------------------------------------ notification / premier plan

    private fun updateForeground(projection: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Menu flottant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indique que le menu flottant de Chess Overlay AI est actif"
            })
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Chess Overlay AI")
            .setContentText(if (projection) "Menu flottant actif · capture d'écran autorisée" else "Menu flottant actif")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Fermer", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (projection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(NOTIF_ID, n, type)
        } else if (projection && Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------------ fenêtres

    private fun baseParams(focusable: Boolean = false) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setContent { ChessOverlayTheme { content() } }
    }

    private fun screen(): Pair<Int, Int> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun addBubble() {
        val (w, h) = screen()
        bubbleParams = baseParams().apply { x = w - dp(72); y = (h * 0.32f).roundToInt() }
        bubble = composeView {
            OverlayBubble(
                state = _state,
                onTap = { expand(!_state.value.expanded) },
                onDrag = { dx, dy -> moveBubble(dx, dy) },
                onDragEnd = ::snapBubble,
            )
        }
        wm.addView(bubble, bubbleParams)
    }

    private fun moveBubble(dx: Float, dy: Float) {
        val v = bubble ?: return
        bubbleParams.x += dx.roundToInt(); bubbleParams.y += dy.roundToInt()
        runCatching { wm.updateViewLayout(v, bubbleParams) }
    }

    /** Positionnement intelligent : la bulle se range contre le bord le plus proche. */
    private fun snapBubble() {
        val v = bubble ?: return
        val (w, h) = screen()
        val bw = v.width.takeIf { it > 0 } ?: dp(60)
        bubbleParams.x = if (bubbleParams.x + bw / 2 < w / 2) dp(6) else w - bw - dp(6)
        bubbleParams.y = bubbleParams.y.coerceIn(dp(40), h - v.height - dp(40))
        runCatching { wm.updateViewLayout(v, bubbleParams) }
    }

    private fun expand(open: Boolean) {
        _state.update { it.copy(expanded = open) }
        if (open && panel == null) {
            val (w, h) = screen()
            val scale = container.settings.value.overlayScale
            val pw = (dp(340) * scale).roundToInt().coerceAtMost(w - dp(16))
            panelParams = baseParams().apply {
                width = pw
                alpha = container.settings.value.overlayAlpha
                // Place le panneau du côté de la bulle sans sortir de l'écran.
                x = if (bubbleParams.x > w / 2) w - pw - dp(8) else dp(8)
                y = (bubbleParams.y - dp(40)).coerceIn(dp(32), (h * 0.45f).roundToInt())
            }
            panel = composeView {
                OverlayPanel(
                    state = _state,
                    settingsFlow = container.settings,
                    scale = scale,
                    actions = panelActions,
                    onDrag = { dx, dy -> movePanel(dx, dy) },
                )
            }
            wm.addView(panel, panelParams)
        } else if (!open) {
            panel?.let { runCatching { wm.removeView(it) } }
            panel = null
        }
    }

    private fun movePanel(dx: Float, dy: Float) {
        val v = panel ?: return
        val (w, h) = screen()
        panelParams.x = (panelParams.x + dx.roundToInt()).coerceIn(-panelParams.width / 2, w - panelParams.width / 2)
        panelParams.y = (panelParams.y + dy.roundToInt()).coerceIn(0, h - dp(80))
        runCatching { wm.updateViewLayout(v, panelParams) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        capturer.onConfigurationChanged()
        snapBubble()
        panel?.let { v ->
            val (w, h) = screen()
            panelParams.width = panelParams.width.coerceAtMost(w - dp(16))
            panelParams.x = panelParams.x.coerceIn(0, (w - panelParams.width).coerceAtLeast(0))
            panelParams.y = panelParams.y.coerceIn(0, (h - dp(120)).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(v, panelParams) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    // ------------------------------------------------------------------ actions du panneau

    private fun setPhase(p: OverlayPhase) = _state.update { it.copy(phase = p) }

    val panelActions = object : OverlayActions {
        override fun capture() = this@OverlayService.capture()
        override fun collapse() = expand(false)
        override fun close() { stopSelf() }
        override fun setSide(side: PieceColor) {
            when (val p = _state.value.phase) {
                is OverlayPhase.Recognized -> setPhase(p.copy(side = side))
                else -> {}
            }
        }
        override fun confirmAndAnalyze() = analyze()
        override fun stopAnalysis() { container.engines.analysisEngine.stop() }
        override fun openEditor() {
            val p = _state.value.phase
            if (p is OverlayPhase.Error && p.canFrame) {
                // Échiquier non trouvé : la capture est ouverte dans l'application pour un cadrage manuel.
                startActivity(Intent(this@OverlayService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_RECOGNITION, true)
                    .putExtra(MainActivity.EXTRA_ERROR, p.message))
                expand(false)
                return
            }
            val side = when (p) { is OverlayPhase.Recognized -> p.side; is OverlayPhase.Analyzing -> p.side; else -> PieceColor.WHITE }
            startActivity(Intent(this@OverlayService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_EDIT_RECOGNITION, true)
                .putExtra(MainActivity.EXTRA_SIDE_WHITE, side == PieceColor.WHITE))
            expand(false)
        }
        override fun openAnalysis() {
            val p = _state.value.phase as? OverlayPhase.Analyzing ?: return
            startActivity(Intent(this@OverlayService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_FEN, p.session.result.toPosition(p.side).toFen()))
            expand(false)
        }
        override fun openApp() {
            startActivity(Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            expand(false)
        }
        override fun reset() = setPhase(OverlayPhase.Idle)
    }

    private fun capture() {
        analysisJob?.cancel()
        if (!capturer.isActive) {
            // Demande de consentement Android (obligatoire à chaque nouvelle session de capture).
            captureAfterGrant = true
            startActivity(Intent(this, CaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        lifecycleScope.launch {
            setPhase(OverlayPhase.Working("Capture de l'écran…"))
            // Masque le menu flottant pour qu'il n'apparaisse pas sur la capture.
            bubble?.visibility = View.INVISIBLE
            panel?.visibility = View.INVISIBLE
            val hiddenAt = System.nanoTime()
            delay(350)
            val bmp = runCatching { capturer.capture(hiddenAt) }
            bubble?.visibility = View.VISIBLE
            panel?.visibility = View.VISIBLE
            if (!_state.value.expanded) expand(true)
            val bitmap = bmp.getOrElse {
                val why = DiagnosticLog.record("capture", it)
                setPhase(OverlayPhase.Error("La capture d'écran n'a pas abouti ($why). Réessayez."))
                return@launch
            }
            setPhase(OverlayPhase.Working("Reconnaissance de l'échiquier…"))
            try {
                val session = container.recognition.recognize(bitmap)
                setPhase(OverlayPhase.Recognized(session, session.result.suggestedSideToMove ?: if (session.result.whiteAtBottom) PieceColor.WHITE else PieceColor.BLACK))
            } catch (e: Exception) {
                val msg = if (e is com.chessoverlay.vision.RecognitionException) e.message!!
                else "Erreur pendant la reconnaissance (${DiagnosticLog.record("reconnaissance", e)})."
                setPhase(OverlayPhase.Error(msg, canFrame = container.recognition.lastImage.value != null))
            }
        }
    }

    private fun analyze() {
        val p = _state.value.phase as? OverlayPhase.Recognized ?: return
        val pos = p.session.result.toPosition(p.side)
        val errors = pos.validationErrors()
        if (errors.isNotEmpty()) {
            DiagnosticLog.note("position", "invalide après reconnaissance : ${errors.joinToString(" ")} (${pos.toFen()})")
            setPhase(OverlayPhase.Error("Position impossible : ${errors.first()} Corrigez-la dans l'application."))
            return
        }
        val cfg = container.settings.value
        analysisJob?.cancel()
        setPhase(OverlayPhase.Analyzing(p.session, p.side, null, running = true))
        analysisJob = lifecycleScope.launch {
            try {
                container.analysis.analyze(pos, AnalysisSettings(depth = cfg.effectiveDepth, moveTimeMs = cfg.moveTimeMs.takeIf { it > 0 }?.toLong(), multiPv = cfg.multiPv))
                    .collect { a -> setPhase(OverlayPhase.Analyzing(p.session, p.side, a, running = !a.finished)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                setPhase(OverlayPhase.Error("Erreur du moteur Stockfish (${DiagnosticLog.record("moteur", e)})."))
            }
        }
    }

    override fun onDestroy() {
        analysisJob?.cancel()
        capturer.shutdown()
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        bubble = null; panel = null
        _running.value = false
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "com.chessoverlay.overlay.STOP"
        private const val ACTION_GRANTED = "com.chessoverlay.overlay.GRANTED"
        private const val ACTION_DENIED = "com.chessoverlay.overlay.DENIED"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        @Volatile private var instance: OverlayService? = null

        fun start(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_STOP))
        }

        fun onProjectionGranted(ctx: Context, code: Int, data: Intent) {
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_GRANTED).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }

        fun onProjectionDenied(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_DENIED))
        }
    }
}

interface OverlayActions {
    fun capture()
    fun collapse()
    fun close()
    fun setSide(side: PieceColor)
    fun confirmAndAnalyze()
    fun stopAnalysis()
    fun openEditor()
    fun openAnalysis()
    fun openApp()
    fun reset()
}
