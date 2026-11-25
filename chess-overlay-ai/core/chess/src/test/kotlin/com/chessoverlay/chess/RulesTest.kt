package com.chessoverlay.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun perft(p: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = p.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(p.play(it), depth - 1) }
    }

    @Test fun perftStartPosition() {
        val p = Position.initial()
        assertEquals(20L, perft(p, 1))
        assertEquals(400L, perft(p, 2))
        assertEquals(8902L, perft(p, 3))
        assertEquals(197281L, perft(p, 4))
    }

    @Test fun perftKiwipete() {
        // Position de test classique couvrant roques, prises en passant et promotions.
        val p = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(p, 1))
        assertEquals(2039L, perft(p, 2))
        assertEquals(97862L, perft(p, 3))
    }

    @Test fun perftPosition3EnPassantAndPins() {
        val p = Position.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(p, 1))
        assertEquals(191L, perft(p, 2))
        assertEquals(2812L, perft(p, 3))
        assertEquals(43238L, perft(p, 4))
    }

    @Test fun perftPosition4Promotions() {
        val p = Position.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(p, 1))
        assertEquals(264L, perft(p, 2))
        assertEquals(9467L, perft(p, 3))
    }

    @Test fun perftPosition5() {
        val p = Position.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(p, 1))
        assertEquals(1486L, perft(p, 2))
        assertEquals(62379L, perft(p, 3))
    }

    @Test fun fenRoundTrip() {
        val fens = listOf(
            Position.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            "8/8/8/8/8/8/6k1/4K2R b K - 12 40",
        )
        for (f in fens) assertEquals(f, Position.fromFen(f).toFen())
    }

    @Test fun invalidFenGivesFrenchError() {
        val e = runCatching { Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1") }.exceptionOrNull()
        assertTrue(e is ChessFormatException)
        assertTrue(e!!.message!!.contains("8 rangées"))
        assertNull(Position.fromFenOrNull("xyz"))
    }

    @Test fun kingsideAndQueensideCastling() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val oo = Move.fromUci("e1g1")!!
        val ooo = Move.fromUci("e1c1")!!
        assertTrue(p.isLegal(oo)); assertTrue(p.isLegal(ooo))
        assertEquals(MoveKind.CASTLE_KINGSIDE, p.moveKind(oo))
        assertEquals(MoveKind.CASTLE_QUEENSIDE, p.moveKind(ooo))
        val after = p.play(oo)
        assertEquals(Piece.of(PieceType.ROOK, PieceColor.WHITE), after.pieceAt(Sq.F1))
        assertNull(after.pieceAt(Sq.H1))
        assertEquals("kq", after.castling.toFen())
        assertEquals("O-O", San.toSan(p, oo)); assertEquals("O-O-O", San.toSan(p, ooo))
    }

    @Test fun castlingForbiddenThroughCheck() {
        // La tour noire en f8 contrôle f1 : le petit roque est interdit, le grand reste possible.
        val p = Position.fromFen("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(p.isLegal(Move.fromUci("e1g1")!!))
        assertTrue(p.isLegal(Move.fromUci("e1c1")!!))
        // En échec : aucun roque.
        val inCheck = Position.fromFen("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(inCheck.legalMoves().any { inCheck.isCastling(it) })
    }

    @Test fun enPassant() {
        val p = Position.fromFen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        val ep = Move.fromUci("e5f6")!!
        assertTrue(p.isLegal(ep))
        assertEquals(MoveKind.EN_PASSANT, p.moveKind(ep))
        assertEquals(Sq.parse("f5"), p.capturedSquare(ep))
        val after = p.play(ep)
        assertNull(after.pieceAt(Sq.parse("f5")))
        assertEquals("exf6", San.toSan(p, ep))
        // La prise en passant sur d6 n'est plus possible (le coup n'a pas été joué immédiatement).
        assertFalse(p.isLegal(Move.fromUci("e5d6")!!))
    }

    @Test fun promotion() {
        val p = Position.fromFen("1n5k/P7/8/8/8/8/8/K7 w - - 0 1")
        val moves = p.legalMovesFrom(Sq.parse("a7"))
        assertEquals(8, moves.size) // a8=D/T/F/C et axb8=D/T/F/C
        val promo = Move.fromUci("a7b8q")!!
        assertEquals(MoveKind.PROMOTION_CAPTURE, p.moveKind(promo))
        assertEquals("axb8=Q+", San.toSan(p, promo))
        assertEquals("axb8=D+", San.toSan(p, promo, french = true))
        assertEquals(Piece.of(PieceType.QUEEN, PieceColor.WHITE), p.play(promo).pieceAt(Sq.parse("b8")))
        assertEquals(Move.fromUci("a7a8n"), San.parse(p, "a8=N"))
    }

    @Test fun checkmateAndStalemate() {
        val mate = Game.new().playSan("f3").playSan("e5").playSan("g4").playSan("Qh4#")
        assertTrue(mate.current.isCheckmate)
        assertEquals(GameStatus.CHECKMATE, mate.status)
        assertEquals("0-1", mate.result)
        assertEquals("Qh4#", mate.sans.last())

        val stale = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(stale.isStalemate)
        assertFalse(stale.isCheck)
    }

    @Test fun illegalMovesRejected() {
        val p = Position.initial()
        assertFalse(p.isLegal(Move.fromUci("e2e5")!!))
        assertFalse(p.isLegal(Move.fromUci("g1g3")!!))
        // Pièce clouée : le cavalier e7 protège son roi de la dame e2.
        val pinned = Position.fromFen("4k3/4n3/8/8/8/8/4Q3/4K3 b - - 0 1")
        assertTrue(pinned.legalMovesFrom(Sq.parse("e7")).isEmpty())
        assertTrue(runCatching { p.play(Move.fromUci("e2e5")!!) }.isFailure)
    }

    @Test fun sanDisambiguation() {
        val p = Position.fromFen("4k3/8/8/8/8/8/8/R4RK1 w - - 0 1")
        assertEquals("Rad1", San.toSan(p, Move.fromUci("a1d1")!!))
        val q = Position.fromFen("4k3/8/8/1N6/8/1N6/8/4K3 w - - 0 1")
        assertEquals("N5d4", San.toSan(q, Move.fromUci("b5d4")!!))
        assertEquals(Move.fromUci("b3d4"), San.parse(q, "N3d4"))
        assertEquals(Move.fromUci("b3d4"), San.parse(q, "C3d4"))
    }

    @Test fun frenchNotationParsing() {
        val p = Position.initial()
        assertEquals(Move.fromUci("g1f3"), San.parse(p, "Cf3"))
        assertEquals(Move.fromUci("e2e4"), San.parse(p, "e4"))
        assertEquals(Move.fromUci("e2e4"), San.parse(p, "e2-e4"))
        // « R » en français = roi quand aucune tour ne peut jouer le coup.
        val k = Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals(Move.fromUci("e1e2"), San.parse(k, "Re2"))
    }

    @Test fun insufficientMaterial() {
        assertTrue(Position.fromFen("8/8/4k3/8/8/2B5/8/4K3 w - - 0 1").isInsufficientMaterial)
        assertFalse(Position.fromFen("8/8/4k3/8/8/2R5/8/4K3 w - - 0 1").isInsufficientMaterial)
    }

    @Test fun threefoldRepetition() {
        var g = Game.new()
        repeat(2) { for (s in listOf("Nf3", "Nf6", "Ng1", "Ng8")) g = g.playSan(s) }
        assertEquals(GameStatus.THREEFOLD_REPETITION, g.status)
    }

    @Test fun validation() {
        assertTrue(Position.initial().validationErrors().isEmpty())
        val noKing = Position.fromFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(noKing.validationErrors().any { it.contains("roi noir") })
        val wrongSide = Position.fromFen("4k3/8/8/8/8/8/4R3/4K3 w - - 0 1")
        assertTrue(wrongSide.validationErrors().any { it.contains("trait") })
        val pawnRank = Position.fromFen("P3k3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(pawnRank.validationErrors().isNotEmpty())
    }

    @Test fun pgnRoundTrip() {
        val pgn = """
            [Event "Partie de test"]
            [White "Kasparov"]
            [Black "Topalov"]
            [Result "1-0"]

            1. e4 {commentaire} d6 2. d4 Nf6 3. Nc3 g6 (3... e5 4. dxe5) 4. Be3 Bg7 5. Qd2 c6 6. f3 b5
            7. Nge2 Nbd7 8. Bh6 Bxh6 9. Qxh6 Bb7 10. a3 e5 11. O-O-O Qe7 12. Kb1 a6 $1 13. Nc1 O-O-O 1-0
        """.trimIndent()
        val g = Pgn.parse(pgn)
        assertEquals(26, g.ply)
        assertEquals("Kasparov", g.tags["White"])
        assertEquals("O-O-O", g.sans.last())
        val again = Pgn.parse(g.toPgn())
        assertEquals(g.current.toFen(), again.current.toFen())
        assertNotNull(again.tags["Result"])
    }

    @Test fun pgnWithFenAndBlackToMove() {
        val pgn = "[FEN \"4k3/8/8/8/8/8/4P3/4K3 b - - 0 10\"]\n[SetUp \"1\"]\n\n10... Kd7 11. e4 *"
        val g = Pgn.parse(pgn)
        assertEquals(2, g.ply)
        assertTrue(g.toPgn().contains("10... Kd7 11. e4"))
    }

    @Test fun multipleGames() {
        val text = "[Event \"A\"]\n\n1. e4 e5 1-0\n\n[Event \"B\"]\n\n1. d4 d5 0-1\n"
        val games = Pgn.parseAll(text)
        assertEquals(2, games.size)
        assertEquals("B", games[1].tags["Event"])
    }

    @Test fun uciLineToSan() {
        val sans = San.uciLineToSan(Position.initial(), listOf("e2e4", "e7e5", "g1f3", "b8c6"), french = true)
        assertEquals(listOf("e4", "e5", "Cf3", "Cc6"), sans)
    }

    @Test fun frenchPieceNames() {
        assertEquals("Cavalier blanc", Piece.of(PieceType.KNIGHT, PieceColor.WHITE).frenchName)
        assertEquals("Dame noire", Piece.of(PieceType.QUEEN, PieceColor.BLACK).frenchName)
        assertEquals("Tour blanche", Piece.of(PieceType.ROOK, PieceColor.WHITE).frenchName)
    }
}
