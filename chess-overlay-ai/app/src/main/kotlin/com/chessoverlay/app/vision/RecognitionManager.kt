package com.chessoverlay.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.ContextCompat
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.vision.BoardRecognizer
import com.chessoverlay.vision.PieceTemplateStore
import com.chessoverlay.vision.RecognitionException
import com.chessoverlay.vision.RecognitionResult
import com.chessoverlay.vision.RgbImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Résultat d'une reconnaissance, conservé pour l'édition et l'apprentissage à partir des corrections. */
class RecognitionSession(val bitmap: Bitmap, val image: RgbImage, val result: RecognitionResult)

/**
 * Gère le module de vision : gabarits de pièces (initialisés à partir des jeux de pièces intégrés,
 * puis enrichis par les corrections de l'utilisateur), reconnaissance et persistance.
 */
class RecognitionManager(private val context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "piece_templates.txt")
    private val mutex = Mutex()
    private var recognizer: BoardRecognizer? = null

    private val _session = MutableStateFlow<RecognitionSession?>(null)
    val session: StateFlow<RecognitionSession?> = _session

    private val _templateCount = MutableStateFlow(0)
    val templateCount: StateFlow<Int> = _templateCount

    private suspend fun recognizer(): BoardRecognizer = mutex.withLock {
        recognizer ?: withContext(Dispatchers.Default) {
            val store = runCatching { if (file.exists()) PieceTemplateStore.deserialize(file.readText()) else null }.getOrNull()
                ?: PieceTemplateStore()
            val r = BoardRecognizer(store)
            if (store.size == 0) bootstrap(r)
            recognizer = r
            _templateCount.value = store.size
            r
        }
    }

    /**
     * Gabarits de départ : chaque pièce de chaque jeu intégré est dessinée sur des fonds de cases
     * courants (bois, vert tournoi, graphite) pour que la reconnaissance fonctionne dès la première
     * utilisation sur les sites qui utilisent des pièces similaires.
     */
    private fun bootstrap(r: BoardRecognizer) {
        val backgrounds = intArrayOf(
            0xFFF0D9B5.toInt(), 0xFFB58863.toInt(), // bois (style lichess)
            0xFFEBECD0.toInt(), 0xFF739552.toInt(), // vert (style tournoi)
            0xFF9A93B8.toInt(), 0xFF4B4566.toInt(), // graphite (thème de l'application)
        )
        val size = 96
        for (set in PieceSet.entries) for (c in PieceColor.entries) for (t in PieceType.entries) {
            val piece = Piece.of(t, c)
            val drawable = ContextCompat.getDrawable(context, set.drawable(piece)) ?: continue
            for (bg in backgrounds) {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(bg)
                val inset = (size * 0.04f).toInt()
                drawable.setBounds(inset, inset, size - inset, size - inset)
                drawable.draw(canvas)
                r.learnTemplate(piece, bmp.toRgbImage())
                bmp.recycle()
            }
        }
        save(r)
    }

    private fun save(r: BoardRecognizer) {
        runCatching { file.writeText(r.templates.serialize()) }
        _templateCount.value = r.templates.size
    }

    /** Reconnaît la position sur une capture. Lève [RecognitionException] (message en français) si aucun échiquier n'est trouvé. */
    suspend fun recognize(bitmap: Bitmap, forceWhiteAtBottom: Boolean? = null): RecognitionSession {
        val r = recognizer()
        return withContext(Dispatchers.Default) {
            val img = bitmap.toRgbImage()
            val result = r.recognize(img, forceWhiteAtBottom = forceWhiteAtBottom)
            RecognitionSession(bitmap, img, result).also { _session.value = it }
        }
    }

    suspend fun recognizeUri(uri: Uri, forceWhiteAtBottom: Boolean? = null): RecognitionSession {
        val bitmap = withContext(Dispatchers.IO) { decodeUri(uri) }
            ?: throw RecognitionException("Impossible d'ouvrir cette image.")
        return recognize(bitmap, forceWhiteAtBottom)
    }

    /** Apprend à partir de la position corrigée par l'utilisateur, puis sauvegarde les gabarits. */
    suspend fun learnFromCorrection(session: RecognitionSession, corrected: Array<Piece?>) {
        val r = recognizer()
        withContext(Dispatchers.Default) {
            r.learnFromCorrection(session.image, session.result, corrected)
            save(r)
        }
    }

    suspend fun resetTemplates() {
        mutex.withLock {
            recognizer = null
            file.delete()
        }
        recognizer()
    }

    fun clearSession() { _session.value = null }

    private fun decodeUri(uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        // Limite la taille en mémoire (une capture du S23 Ultra fait 1440 × 3088).
        var sample = 1
        while (opts.outWidth / sample > 2200 || opts.outHeight / sample > 3400) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
    }
}

fun Bitmap.toRgbImage(): RgbImage {
    val src = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    val px = IntArray(src.width * src.height)
    src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
    return RgbImage(src.width, src.height, px)
}
