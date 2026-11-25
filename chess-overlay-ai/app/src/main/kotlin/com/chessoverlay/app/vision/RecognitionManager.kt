package com.chessoverlay.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.chessoverlay.app.util.DiagnosticLog
import com.chessoverlay.chess.Piece
import com.chessoverlay.vision.BoardRecognizer
import com.chessoverlay.vision.BoardRect
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
 * Gère le module de vision : gabarits de pièces (banque par défaut de 7 jeux de pièces libres, enrichie
 * par la calibration et les corrections de l'utilisateur), reconnaissance et persistance.
 */
class RecognitionManager(private val context: Context, private val scope: CoroutineScope) {

    // Nouveau nom de fichier : les gabarits de la version 1.0.0 (format v1) sont remplacés.
    private val file = File(context.filesDir, "piece_templates_v2.txt")
    private val mutex = Mutex()
    private var recognizer: BoardRecognizer? = null

    private val _session = MutableStateFlow<RecognitionSession?>(null)
    val session: StateFlow<RecognitionSession?> = _session

    /** Dernière image capturée ou importée, conservée même si la reconnaissance a échoué. */
    private val _lastImage = MutableStateFlow<Bitmap?>(null)
    val lastImage: StateFlow<Bitmap?> = _lastImage

    private val _templateCount = MutableStateFlow(0)
    val templateCount: StateFlow<Int> = _templateCount

    private suspend fun recognizer(): BoardRecognizer = mutex.withLock {
        recognizer ?: withContext(Dispatchers.Default) {
            File(context.filesDir, "piece_templates.txt").delete()
            val saved = runCatching { if (file.exists()) PieceTemplateStore.deserialize(file.readText()) else null }.getOrNull()
            val store = saved?.takeIf { it.size > 0 } ?: PieceTemplateStore.loadDefault()
            val r = BoardRecognizer(store)
            if (saved == null || saved.size == 0) save(r)
            recognizer = r
            _templateCount.value = store.size
            r
        }
    }

    private fun save(r: BoardRecognizer) {
        runCatching { file.writeText(r.templates.serialize()) }
        _templateCount.value = r.templates.size
    }

    /**
     * Reconnaît la position sur une capture. Lève [RecognitionException] (message en français) si aucun
     * échiquier n'est trouvé ; l'image reste alors disponible dans [lastImage] pour un recadrage manuel.
     * @param hint cadre de l'échiquier choisi à la main par l'utilisateur, ou null pour la détection automatique.
     */
    suspend fun recognize(bitmap: Bitmap, forceWhiteAtBottom: Boolean? = null, hint: BoardRect? = null): RecognitionSession {
        _lastImage.value = bitmap
        _session.value = null
        val r = recognizer()
        return withContext(Dispatchers.Default) {
            val img = bitmap.toRgbImage()
            try {
                val result = r.recognize(img, hint = hint, forceWhiteAtBottom = forceWhiteAtBottom)
                RecognitionSession(bitmap, img, result).also { _session.value = it }
            } catch (e: RecognitionException) {
                DiagnosticLog.note("reconnaissance", "${bitmap.width}x${bitmap.height} : ${e.message}")
                throw e
            }
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
        // Limite la taille en mémoire (une capture du S23 Ultra fait jusqu'à 1440 × 3088).
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
