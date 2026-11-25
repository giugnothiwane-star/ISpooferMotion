package com.chessoverlay.engine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class UciParserTest {
    @Test fun parsesInfoLine() {
        val l = UciParser.parseInfo(
            "info depth 14 seldepth 20 multipv 2 score cp -24 nodes 164103 nps 692417 hashfull 56 tbhits 0 time 237 pv b1c3 b8c6 g1f3"
        )!!
        assertEquals(14, l.depth); assertEquals(2, l.multipv); assertEquals(-24, l.score.centipawns)
        assertEquals(listOf("b1c3", "b8c6", "g1f3"), l.pv)
        assertEquals("-0,24", l.score.format())
    }

    @Test fun parsesMateAndBounds() {
        val l = UciParser.parseInfo("info depth 5 score mate -2 upperbound nodes 10 pv e1e2")!!
        assertEquals(-2, l.score.mate); assertTrue(l.score.upperBound)
        assertEquals("-M2", l.score.format())
        assertNull(UciParser.parseInfo("info string NNUE evaluation using nn-1c0000000000.nnue"))
    }

    @Test fun parsesBestMove() {
        assertEquals(BestMove("e2e4", "e7e5"), UciParser.parseBestMove("bestmove e2e4 ponder e7e5"))
        assertEquals(BestMove(null, null), UciParser.parseBestMove("bestmove (none)"))
    }

    @Test fun scoreOrderingAndPerspective() {
        assertTrue(EngineScore(null, 2).sortValue > EngineScore(null, 5).sortValue)
        assertTrue(EngineScore(null, 5).sortValue > EngineScore(900, null).sortValue)
        assertEquals(-150, EngineScore(150, null).forWhite(whiteToMove = false).centipawns)
        assertEquals(50.0, EngineScore.ZERO.winPercent(), 0.001)
    }

    @Test fun goCommand() {
        assertEquals("go depth 12", SearchLimits(depth = 12).toGoCommand())
        assertEquals("go movetime 500 searchmoves e2e4 d2d4", SearchLimits(moveTimeMs = 500, searchMoves = listOf("e2e4", "d2d4")).toGoCommand())
    }
}

/** Tests d'intégration avec le vrai binaire Stockfish (compilé par scripts/build_stockfish.sh host). */
class StockfishIntegrationTest {
    private val path = System.getProperty("stockfish.path") ?: "build/stockfish-host/stockfish"

    private fun engine(): StockfishEngine {
        assumeTrue("Binaire Stockfish absent : $path", File(path).canExecute())
        return StockfishEngine(listOf(path))
    }

    @Test fun findsMateInOne() = runBlocking {
        val e = engine()
        try {
            e.configure(EngineOptions(threads = 2, hashMb = 32))
            // Mat du couloir : Te8#.
            val r = e.search("6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1", SearchLimits(depth = 10))
            assertEquals("e1e8", r.bestMove?.move)
            assertEquals(1, r.best?.score?.mate)
            assertTrue(e.name.startsWith("Stockfish"))
        } finally { e.close() }
    }

    @Test fun multiPvReturnsThreeLines() = runBlocking {
        val e = engine()
        try {
            val r = e.search("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", SearchLimits(depth = 12, multiPv = 3))
            assertEquals(3, r.lines.size)
            assertEquals(listOf(1, 2, 3), r.lines.map { it.multipv })
            assertTrue(r.lines[0].score.sortValue >= r.lines[2].score.sortValue)
            assertNotNull(r.bestMove?.move)
        } finally { e.close() }
    }

    @Test fun searchMovesRestrictsSearch() = runBlocking {
        val e = engine()
        try {
            val r = e.search(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                SearchLimits(depth = 8, searchMoves = listOf("a2a3")),
            )
            assertEquals("a2a3", r.bestMove?.move)
        } finally { e.close() }
    }

    @Test fun cancellingInfiniteSearchStopsEngine() = runBlocking {
        val e = engine()
        try {
            val first = e.analyze(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                SearchLimits(infinite = true),
            ).first { it.depth >= 6 }
            assertTrue(first.depth >= 6)
            // Le moteur doit rester utilisable après l'annulation.
            val again = e.analyze("6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1", SearchLimits(depth = 6)).toList()
            assertTrue(again.last().finished)
            assertEquals("e1e8", again.last().bestMove?.move)
        } finally { e.close() }
    }

    @Test fun stopEndsInfiniteSearch() = runBlocking {
        val e = engine()
        try {
            e.start()
            val t = Thread { Thread.sleep(400); e.stop() }
            t.start()
            val r = e.search("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", SearchLimits(infinite = true))
            assertTrue(r.finished)
            assertNotNull(r.bestMove?.move)
        } finally { e.close() }
    }

    @Test fun limitStrengthOptionsAccepted() = runBlocking {
        val e = engine()
        try {
            e.configure(EngineOptions(threads = 1, hashMb = 16, limitElo = 1500))
            e.newGame()
            val r = e.search(StartFen, SearchLimits(moveTimeMs = 200))
            assertNotNull(r.bestMove?.move)
        } finally { e.close() }
    }
}

private val StartFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
