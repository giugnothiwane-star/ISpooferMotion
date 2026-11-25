package com.chessoverlay.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.chessoverlay.analysis.AnalysisService
import com.chessoverlay.analysis.AnalysisSettings
import com.chessoverlay.analysis.TacticalScanner
import com.chessoverlay.app.ui.analysis.BestMovePanel
import com.chessoverlay.app.ui.analysis.CapturesPanel
import com.chessoverlay.app.ui.analysis.LinesPanel
import com.chessoverlay.chess.Position
import com.chessoverlay.engine.StockfishEngine
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.BoardTheme
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.AppBackground
import com.chessoverlay.ui.components.EvalBar
import com.chessoverlay.ui.theme.ChessOverlayTheme
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Rendus de l'interface avec de VRAIS résultats de Stockfish (binaire hôte). */
class ScreenRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6_PRO.copy(softButtons = false),
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private fun service(): Pair<StockfishEngine, AnalysisService> {
        val path = System.getProperty("stockfish.path") ?: ""
        assumeTrue("Stockfish hôte absent", File(path).canExecute())
        val e = StockfishEngine(listOf(path))
        return e to AnalysisService(e)
    }

    private fun render(fen: String, tab: Int, theme: BoardTheme, pieces: PieceSet) {
        val (engine, svc) = service()
        val pos = Position.fromFen(fen)
        val analysis = runBlocking { svc.analyze(pos, AnalysisSettings(depth = 16, multiPv = 3)).last() }
        val captures = runBlocking { svc.evaluateCaptures(pos, 10) }
        engine.close()
        val best = analysis.best!!
        paparazzi.snapshot {
            ChessOverlayTheme {
                AppBackground {
                    Column(Modifier.padding(16.dp)) {
                        EvalBar(best.scoreWhite.winPercent().toFloat(), best.scoreWhite.format())
                        Spacer(Modifier.height(10.dp))
                        ChessBoard(
                            pos, Modifier.fillMaxWidth(), theme = theme, pieceSet = pieces,
                            arrows = analysis.lines.mapIndexed { i, l -> BoardArrow(l.first.from, l.first.to, if (i == 0) Palette.Violet.copy(alpha = 0.88f) else Palette.Cyan.copy(alpha = 0.6f), if (i == 0) 0.2f else 0.13f) },
                            marks = listOf(SquareMark(best.first.from, Palette.Violet, MarkStyle.RING), SquareMark(best.first.to, Palette.Cyan)),
                            animate = false,
                        )
                        Spacer(Modifier.height(14.dp))
                        when (tab) {
                            0 -> BestMovePanel(analysis, false, captures, false, french = true)
                            1 -> LinesPanel(analysis, null, {}, {})
                            else -> CapturesPanel(captures, false, TacticalScanner.scan(pos))
                        }
                    }
                }
            }
        }
    }

    /** Coup du berger : Dxf7# (capture + mat). */
    @Test fun bestMoveMateCapture() = render("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", 0, BoardTheme.GRAPHITE, PieceSet.CBURNETT)

    /** Milieu de partie : trois variantes. */
    @Test fun variants() = render("r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP3PPP/R2QKB1R w KQ - 0 8", 1, BoardTheme.AMETHYST, PieceSet.MERIDA)

    /** Captures évaluées par Stockfish, dont une capture empoisonnée. */
    @Test fun captures() = render("rnb1kbnr/pppp1ppp/8/4p3/4P1q1/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", 2, BoardTheme.GLACIER, PieceSet.CHESSNUT)
}
