package com.chessoverlay.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Capture d'écran via l'API officielle MediaProjection. Android affiche lui-même la demande de
 * consentement et l'indicateur de partage d'écran ; la capture s'arrête dès que l'utilisateur le décide.
 *
 * L'écran virtuel ne produit une image que lorsque le contenu change : la dernière image reçue est
 * donc conservée, ce qui permet de capturer aussi un écran immobile.
 */
class ScreenCapturer(private val context: Context, private val onStopped: () -> Unit) {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val thread = HandlerThread("capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private var latest: Image? = null
    private var latestTime = 0L
    @Volatile private var pending: CompletableDeferred<Bitmap>? = null
    @Volatile private var pendingSince = 0L

    val isActive: Boolean get() = projection != null

    fun start(resultCode: Int, data: Intent) {
        stop()
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(resultCode, data) ?: throw IllegalStateException("Autorisation de capture refusée.")
        // Obligatoire depuis Android 14 : enregistrer le rappel avant de créer l'écran virtuel.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
                onStopped()
            }
        }, handler)
        projection = p
        createDisplay()
    }

    private fun metrics(): DisplayMetrics {
        val wm = context.getSystemService(WindowManager::class.java)
        val m = DisplayMetrics()
        val bounds = wm.maximumWindowMetrics.bounds
        m.widthPixels = bounds.width(); m.heightPixels = bounds.height()
        m.densityDpi = context.resources.displayMetrics.densityDpi
        return m
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * img.width
        val full = Bitmap.createBitmap(img.width + rowPadding / plane.pixelStride, img.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        full.copyPixelsFromBuffer(plane.buffer)
        return if (full.width == img.width) full else Bitmap.createBitmap(full, 0, 0, img.width, img.height).also { full.recycle() }
    }

    @SuppressLint("WrongConstant")
    private fun createDisplay() {
        val p = projection ?: return
        val m = metrics()
        val r = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ ir ->
            val img = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            synchronized(lock) {
                latest?.close()
                latest = img
                latestTime = System.nanoTime()
                val target = pending
                if (target != null && !target.isCompleted && latestTime >= pendingSince) {
                    runCatching { toBitmap(img) }.onSuccess { target.complete(it) }.onFailure { target.completeExceptionally(it) }
                }
            }
        }, handler)
        synchronized(lock) { latest?.close(); latest = null }
        reader?.close()
        reader = r
        val existing = display
        if (existing == null) {
            display = p.createVirtualDisplay(
                "chess-overlay-capture", m.widthPixels, m.heightPixels, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler,
            )
        } else {
            existing.resize(m.widthPixels, m.heightPixels, m.densityDpi)
            existing.surface = r.surface
        }
    }

    /** À appeler après une rotation de l'écran. */
    fun onConfigurationChanged() { if (projection != null) createDisplay() }

    /**
     * Renvoie une image de l'écran postérieure à [sinceNanos] (instant où le menu flottant a été masqué).
     * Si l'écran n'a pas changé depuis, la dernière image disponible est utilisée.
     */
    suspend fun capture(sinceNanos: Long): Bitmap {
        val d = CompletableDeferred<Bitmap>()
        synchronized(lock) {
            val img = latest
            if (img != null && latestTime >= sinceNanos) return toBitmap(img)
            pendingSince = sinceNanos
            pending = d
        }
        val fresh = try { withTimeoutOrNull(1500) { d.await() } } finally { pending = null }
        if (fresh != null) return fresh
        return synchronized(lock) { latest?.let { toBitmap(it) } } ?: throw IllegalStateException("aucune image reçue de l'écran")
    }

    private fun release() {
        runCatching { display?.release() }
        synchronized(lock) { latest?.close(); latest = null }
        runCatching { reader?.close() }
        display = null; reader = null; projection = null
    }

    fun stop() {
        val p = projection
        release()
        runCatching { p?.stop() }
    }

    fun shutdown() {
        stop()
        thread.quitSafely()
    }
}
