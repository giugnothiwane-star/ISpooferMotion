package com.chessoverlay.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.chessoverlay.analysis.AnalysisService
import com.chessoverlay.analysis.AnalysisSettings
import com.chessoverlay.app.overlay.OverlayActions
import com.chessoverlay.app.overlay.OverlayPanel
import com.chessoverlay.app.overlay.OverlayPhase
import com.chessoverlay.app.overlay.OverlayState
import com.chessoverlay.app.vision.RecognitionSession
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.data.AppSettings
import com.chessoverlay.engine.StockfishEngine
import com.chessoverlay.ui.components.AppBackground
import com.chessoverlay.ui.theme.ChessOverlayTheme
import com.chessoverlay.vision.BoardRecognizer
import com.chessoverlay.vision.PieceTemplateStore
import com.chessoverlay.vision.RgbImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Parcours complet du menu flottant sur JVM : capture d'écran réelle (fixture) → reconnaissance
 * → position vérifiée → analyse par le vrai Stockfish → rendu du panneau.
 */
class OverlayRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6_PRO.copy(softButtons = false),
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private val dir = File(System.getProperty("fixtures.dir") ?: "")

    private fun load(name: String): RgbImage {
        // javax.imageio n'est pas visible à la compilation des tests Android : appel par réflexion.
        val img = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, File(dir, "$name.png"))
        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val px = IntArray(w * h)
        cls.getMethod("getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java, IntArray::class.java, Int::class.java, Int::class.java)
            .invoke(img, 0, 0, w, h, px, 0, w)
        return RgbImage(w, h, px)
    }

    private val noop = object : OverlayActions {
        override fun capture() {}; override fun collapse() {}; override fun close() {}
        override fun setSide(side: PieceColor) {}; override fun confirmAndAnalyze() {}; override fun stopAnalysis() {}
        override fun openEditor() {}; override fun openAnalysis() {}; override fun openApp() {}; override fun reset() {}
    }

    private fun session(): RecognitionSession {
        assumeTrue(File(dir, "chesscom_merida_mid.png").exists())
        val recognizer = BoardRecognizer(PieceTemplateStore())
        val sheet = load("templates_merida")
        val s = sheet.height / 2
        for (row in 0 until 2) for ((i, c) in "KQRBNPkqrbnp".withIndex()) {
            recognizer.learnTemplate(Piece.fromFenChar(c)!!, sheet.crop(i * s, row * s, s, s))
        }
        val image = load("chesscom_merida_mid")
        val result = recognizer.recognize(image)
        assertEquals("2r3k1/1q3pp1/p3pn1p/1p1b4/3P4/1B2PN2/PQ3PPP/2R3K1", result.toPosition(PieceColor.WHITE).toFen().substringBefore(' '))
        return RecognitionSession(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), image, result)
    }

    private fun snap(phase: OverlayPhase) {
        val settings = MutableStateFlow(AppSettings(boardTheme = "tournament", pieceStyle = "merida"))
        paparazzi.snapshot {
            ChessOverlayTheme {
                AppBackground {
                    Box(Modifier.padding(16.dp).width(360.dp)) {
                        OverlayPanel(MutableStateFlow(OverlayState(expanded = true, phase = phase)), settings, 1f, noop) { _, _ -> }
                    }
                }
            }
        }
    }

    @Test fun recognizedPosition() = snap(OverlayPhase.Recognized(session(), PieceColor.WHITE))

    @Test fun analyzedPosition() {
        val path = System.getProperty("stockfish.path") ?: ""
        assumeTrue(File(path).canExecute())
        val s = session()
        val engine = StockfishEngine(listOf(path))
        val analysis = runBlocking { AnalysisService(engine).analyze(s.result.toPosition(PieceColor.WHITE), AnalysisSettings(depth = 16, multiPv = 3)).last() }
        engine.close()
        snap(OverlayPhase.Analyzing(s, PieceColor.WHITE, analysis, running = false))
    }
}
