package com.chessoverlay.analysis

import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Position
import com.chessoverlay.engine.EngineOptions
import com.chessoverlay.engine.SearchLimits
import com.chessoverlay.engine.StockfishEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Tests d'intégration avec le vrai binaire Stockfish (build/stockfish-host/stockfish). */
class EngineAnalysisTest {

    companion object {
        private val path = System.getProperty("stockfish.path") ?: "build/stockfish-host/stockfish"
        lateinit var engine: StockfishEngine
        lateinit var service: AnalysisService

        @JvmStatic @BeforeClass fun setUp() {
            assumeTrue("Binaire Stockfish absent : $path", File(path).canExecute())
            engine = StockfishEngine(listOf(path))
            // Un seul thread : résultats déterministes.
            runBlocking { engine.configure(EngineOptions(threads = 1, hashMb = 64)) }
            service = AnalysisService(engine)
        }

        @JvmStatic @AfterClass fun tearDown() {
            if (::engine.isInitialized) engine.close()
        }
    }

    @Test fun fullAnalysisWithThreeLines() = runBlocking {
        val pos = Game.new().playSan("e4").playSan("e5").current
        val all = service.analyze(pos, AnalysisSettings(depth = 14, multiPv = 3)).toList()
        val a = all.last()
        assertTrue(a.finished)
        assertEquals(3, a.lines.size)
        assertEquals(listOf(1, 2, 3), a.lines.map { it.rank })
        val best = a.best!!
        assertTrue(best.explanation.isNotEmpty())
        assertTrue(best.explanation.first(), best.explanation.first().contains("Stockfish"))
        assertNotNull(a.opponentBestReply)
        assertTrue(a.followUp.isNotEmpty())
        assertTrue(best.sanLine.startsWith("2. "))
        assertEquals(best.score.forWhite(true), best.scoreWhite)
        // Toutes les variantes sont légales et décrites.
        a.lines.forEach { l -> Game.fromUci(pos, l.moves.map { it.uci }) }
        assertTrue(all.size > 1) // flux progressif
    }

    @Test fun findsMateAndExplainsIt() = runBlocking {
        val pos = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1")
        val a = service.analyze(pos, AnalysisSettings(depth = 10, multiPv = 2)).toList().last()
        assertEquals("d1d8", a.best!!.first.uci)
        assertTrue(a.best!!.explanation.any { it.contains("mat") })
        assertTrue(a.best!!.first.givesMate)
    }

    @Test fun gameOverPositionGivesSingleResult() = runBlocking {
        val mate = Game.new().playSan("f3").playSan("e5").playSan("g4").playSan("Qh4#").current
        val r = service.analyze(mate, AnalysisSettings(depth = 10)).toList()
        assertEquals(1, r.size)
        assertTrue(r[0].gameOverReason!!.contains("Noirs"))
        assertTrue(r[0].lines.isEmpty())
        val stale = service.analyze(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"), AnalysisSettings()).toList()
        assertTrue(stale.single().gameOverReason!!.startsWith("Pat"))
    }

    @Test fun poisonedCaptureIsUnfavorable() = runBlocking {
        // Txa7 gagne un pion (SEE positive) mais abandonne la 1re rangée : Td1#.
        val pos = Position.fromFen("3r2k1/p4ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        val caps = service.evaluateCaptures(pos, depth = 12)
        val rxa7 = caps.single { it.description.uci == "a1a7" }
        assertTrue(rxa7.see > 0)
        assertEquals(CaptureVerdict.UNFAVORABLE, rxa7.verdict)
        assertTrue(rxa7.explanation, rxa7.explanation.contains("mat"))
        assertTrue(rxa7.engineScore!!.mate!! < 0)
    }

    @Test fun freeQueenCaptureIsFavorable() = runBlocking {
        val pos = Position.fromFen("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1")
        val c = service.evaluateCaptures(pos, depth = 10).single()
        assertEquals(CaptureVerdict.FAVORABLE, c.verdict)
        assertEquals(0, c.deltaVsBest)
        assertTrue(c.consequences.any { it.startsWith("Suite calculée") })
        assertTrue(service.evaluateCaptures(Position.initial()).isEmpty())
    }

    @Test fun reviewFindsBlunder() = runBlocking {
        val game = Game.new().playSan("e4").playSan("e5").playSan("Qh5").playSan("Nc6")
            .playSan("Bc4").playSan("Nf6").playSan("Qxf7#")
        var progressCalls = 0
        val review = service.reviewGame(game, depth = 12) { _, _ -> progressCalls++ }
        assertEquals(7, review.moves.size)
        val nf6 = review.moves[5]
        assertEquals("Cf6", nf6.san)
        assertEquals(MoveClassification.BLUNDER, nf6.classification)
        assertNotNull(nf6.bestMoveSan)
        assertTrue(nf6.comment.contains("Gaffe"))
        assertEquals(MoveClassification.BOOK, review.moves[0].classification)
        val mate = review.moves[6]
        assertTrue(mate.classification == MoveClassification.BEST || mate.classification == MoveClassification.EXCELLENT)
        assertTrue(review.accuracyWhite > review.accuracyBlack)
        assertTrue(progressCalls >= game.positions.size)
    }

    /** Chaque exercice est revérifié avec le moteur : coup unique (marge nette) ou mat forcé. */
    @Test fun puzzleSolutionsAreEngineBest() = runBlocking {
        for (p in PuzzleSet.puzzles) {
            var pos = Position.fromFen(p.fen)
            for ((i, uci) in p.solution.withIndex()) {
                val playerTurn = i % 2 == 0
                if (playerTurn) {
                    val r = engine.search(pos.toFen(), SearchLimits(depth = 18, multiPv = 2))
                    val best = r.lines[0]
                    val last = i == p.solution.size - 1
                    val solMove = Move.fromUci(uci)!!
                    if (best.pv[0] != uci) {
                        // Seul cas toléré : plusieurs mats en un au dernier coup.
                        assertTrue("${p.id} étape $i : moteur ${best.pv[0]} au lieu de $uci",
                            last && best.score.mate == 1 && pos.play(pos.normalize(solMove)!!).isCheckmate)
                    }
                    val second = r.lines.getOrNull(1)
                    if (best.score.mate != null) {
                        assertTrue("${p.id} étape $i : pas de mat", best.score.mate!! > 0)
                        if (!last && second != null) {
                            assertTrue("${p.id} étape $i : solution non unique",
                                second.score.mate == null || second.score.mate!! <= 0 || second.score.mate!! > best.score.mate!!)
                        }
                    } else {
                        val margin = best.score.sortValue - (second?.score?.sortValue ?: -100_000)
                        assertTrue("${p.id} étape $i : marge $margin", margin >= 100 && best.score.sortValue >= 100)
                    }
                } else {
                    val r = engine.search(pos.toFen(), SearchLimits(depth = 16))
                    if (r.bestMove?.move != uci) {
                        // Réponse équivalente tolérée : même évaluation que le meilleur coup.
                        val alt = engine.search(pos.toFen(), SearchLimits(depth = 16, searchMoves = listOf(uci)))
                        val d = abs(r.best!!.score.sortValue - alt.best!!.score.sortValue)
                        assertTrue("${p.id} réponse $i : $uci au lieu de ${r.bestMove?.move} (écart $d)", d <= 30)
                    }
                }
                pos = pos.play(pos.normalize(Move.fromUci(uci)!!)!!)
            }
        }
    }

    @Test fun endgameGoalsConfirmedByEngine() = runBlocking {
        for (e in EndgameSet.endgames) {
            val s = engine.search(e.fen, SearchLimits(depth = 20)).best!!.score
            if (e.goal == EndgameSet.WIN) assertTrue("${e.id} : $s", (s.mate ?: 0) > 0 || (s.centipawns ?: 0) >= 150)
            else assertTrue("${e.id} : $s", s.mate == null && abs(s.centipawns!!) <= 80)
        }
    }

    @Test fun explanationMentionsDefenseOfThreatenedPiece() = runBlocking {
        // La dame noire est attaquée par le cavalier : le meilleur coup la met à l'abri.
        val pos = Position.fromFen("rnb1kbnr/pppp1ppp/8/4q3/8/2N2N2/PPPPPPPP/R1BQKB1R b KQkq - 0 1")
        val a = service.analyze(pos, AnalysisSettings(depth = 12, multiPv = 1)).toList().last()
        val best = a.best!!
        assertEquals("Dame noire", best.first.piece.frenchName)
        assertTrue(best.explanation.toString(), best.explanation.any { it.contains("met à l'abri") })
        assertNull(a.gameOverReason)
    }
}
