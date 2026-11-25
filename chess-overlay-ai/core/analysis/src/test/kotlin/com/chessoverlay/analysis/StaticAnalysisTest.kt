package com.chessoverlay.analysis

import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.MoveKind
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Pgn
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveDescriberTest {
    private fun m(u: String) = Move.fromUci(u)!!

    @Test fun normalMoveAndHeadline() {
        val d = MoveDescriber.describe(Position.initial(), m("g1f3"))
        assertEquals("Cavalier blanc : G1 → F3", d.headline)
        assertEquals("Nf3", d.san); assertEquals("Cf3", d.sanFrench)
        assertEquals(MoveKind.NORMAL, d.kind)
        assertEquals("Le Cavalier blanc va de G1 à F3.", d.summaryFr())
    }

    @Test fun castling() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val d = MoveDescriber.describe(p, m("e1g1"))
        assertEquals(MoveKind.CASTLE_KINGSIDE, d.kind)
        assertTrue(d.summaryFr(), d.summaryFr().contains("petit roque") && d.summaryFr().contains("H1 à F1"))
        val q = MoveDescriber.describe(p, m("e1c1"))
        assertTrue(q.summaryFr().contains("grand roque") && q.summaryFr().contains("A1 à D1"))
    }

    @Test fun enPassantCapturedSquareDiffers() {
        val p = Position.fromFen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        val d = MoveDescriber.describe(p, m("e5f6"))
        assertEquals(MoveKind.EN_PASSANT, d.kind)
        assertEquals(Sq.parse("f5"), d.capturedSquare)
        assertEquals(Sq.parse("f6"), d.to)
        assertEquals(PieceType.PAWN, d.captured?.type)
        assertTrue(d.summaryFr(), d.summaryFr().contains("en passant") && d.summaryFr().contains("F5"))
    }

    @Test fun promotionWithCaptureAndCheck() {
        val p = Position.fromFen("1n5k/P7/8/8/8/8/8/K7 w - - 0 1")
        val d = MoveDescriber.describe(p, m("a7b8q"))
        assertEquals(MoveKind.PROMOTION_CAPTURE, d.kind)
        assertTrue(d.givesCheck)
        assertEquals("axb8=D+", d.sanFrench)
        assertTrue(d.summaryFr(), d.summaryFr().contains("se promeut en Dame") && d.summaryFr().contains("Cavalier noir"))
        // Promotion sans précision : dame par défaut.
        assertEquals(PieceType.QUEEN, MoveDescriber.describe(p, m("a7a8")).promotion)
    }

    @Test fun captureSummaryAndMate() {
        val g = Game.new().playSan("e4").playSan("e5").playSan("Bc4").playSan("Nc6").playSan("Qh5").playSan("Nf6")
        val d = MoveDescriber.describe(g.current, m("h5f7"))
        assertTrue(d.givesMate)
        assertEquals("La Dame blanche va de H5 à F7 et capture le Pion noir en F7 : échec et mat !", d.summaryFr())
    }

    @Test fun numberedLineFromBlack() {
        val p = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val line = MoveDescriber.describeLine(p, listOf("c7c5", "g1f3", "d7d6", "zzzz", "e2e4"))
        assertEquals(3, line.size) // s'arrête au coup illisible
        assertEquals("1... c5 2. Cf3 d6", MoveDescriber.numberedLine(p, line))
    }
}

class TacticsTest {
    private fun m(u: String) = Move.fromUci(u)!!

    @Test fun seeValues() {
        // Dame qui prend un pion défendu : perte de la dame contre un pion.
        val p = Position.fromFen("4k3/2p5/3p4/8/8/8/8/3QK3 w - - 0 1")
        assertEquals(100 - 900, TacticalScanner.see(p, m("d1d6")))
        // Pion qui prend un cavalier défendu : gain net.
        val q = Position.fromFen("4k3/8/4p3/3n4/4P3/8/8/4K3 w - - 0 1")
        assertEquals(320 - 100, TacticalScanner.see(q, m("e4d5")))
        // Pièce non protégée.
        val r = Position.fromFen("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1")
        assertEquals(900, TacticalScanner.see(r, m("c3d5")))
        // Rayons X : la tour soutient la tour.
        val x = Position.fromFen("4k3/3r4/8/8/3p4/8/3R4/3RK3 w - - 0 1")
        assertEquals(100, TacticalScanner.see(x, m("d2d4")))
        val y = Position.fromFen("3rk3/3r4/8/8/3p4/8/8/3RK3 w - - 0 1")
        assertEquals(100 - 500, TacticalScanner.see(y, m("d1d4")))
    }

    @Test fun forkDetected() {
        val p = Position.fromFen("q3k3/pp4pp/8/1N6/8/8/PP4PP/4K3 w - - 0 1")
        val motifs = TacticalScanner.motifsAfterMove(p, m("b5c7"))
        val fork = motifs.firstOrNull { it.type == MotifType.FORK }
        assertNotNull(motifs.toString(), fork)
        assertTrue(fork!!.squares.containsAll(listOf(Sq.parse("e8"), Sq.parse("a8"))))
        assertTrue(motifs.any { it.type == MotifType.CHECK })
        // Le rapport global signale la fourchette disponible.
        assertTrue(TacticalScanner.scan(p).motifs.any { it.type == MotifType.FORK && it.move == m("b5c7") })
    }

    @Test fun pinDetected() {
        val p = Position.fromFen("r3k3/pp3ppp/2n5/1B6/3P4/8/PP3PPP/R3K3 w Q - 0 1")
        val pins = TacticalScanner.scan(p).motifs.filter { it.type == MotifType.PIN }
        assertTrue(pins.toString(), pins.any { it.squares == listOf(Sq.parse("b5"), Sq.parse("c6"), Sq.parse("e8")) && it.title == "Clouage absolu" })
        val before = Position.fromFen("r3k3/pp3ppp/2n5/8/3P4/8/PP3PPP/R3KB2 w Q - 0 1")
        assertTrue(TacticalScanner.motifsAfterMove(before, m("f1b5")).any { it.type == MotifType.PIN })
    }

    @Test fun skewerDetected() {
        val p = Position.fromFen("8/8/2k4q/8/8/8/8/R3K3 w - - 0 1")
        val motifs = TacticalScanner.motifsAfterMove(p, m("a1a6"))
        assertTrue(motifs.toString(), motifs.any { it.type == MotifType.SKEWER && Sq.parse("h6") in it.squares })
    }

    @Test fun discoveredAttackDetected() {
        val p = Position.fromFen("6k1/4q3/8/8/4N3/8/8/4R1K1 w - - 0 1")
        val motifs = TacticalScanner.motifsAfterMove(p, m("e4g5"))
        assertTrue(motifs.toString(), motifs.any { it.type == MotifType.DISCOVERED_ATTACK && Sq.parse("e7") in it.squares })
        val dbl = TacticalScanner.motifsAfterMove(Position.fromFen("2q1k3/8/8/8/4N3/8/8/4R1K1 w - - 0 1"), m("e4d6"))
        assertTrue(dbl.any { it.type == MotifType.CHECK && it.description.contains("double") })
    }

    @Test fun mateThreatAndMateInOne() {
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/R4PPP/6K1 w - - 0 1")
        assertTrue(TacticalScanner.motifsAfterMove(p, m("a2d2")).any { it.type == MotifType.MATE_THREAT })
        val scan = TacticalScanner.scan(p)
        assertTrue(scan.motifs.any { it.type == MotifType.MATE_IN_ONE && it.move == m("a2a8") })
        // Côté défenseur : la menace adverse et les parades sont signalées.
        val def = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 b - - 0 1")
        val motifs = TacticalScanner.scan(def).motifs
        assertTrue(motifs.any { it.type == MotifType.MATE_THREAT })
        assertTrue(motifs.any { it.type == MotifType.DEFENSE && it.title == "Parer le mat" })
    }

    @Test fun hangingAndThreatenedPieces() {
        val p = Position.fromFen("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1")
        val r = TacticalScanner.scan(p)
        assertTrue(r.motifs.any { it.type == MotifType.HANGING_PIECE && it.squares == listOf(Sq.parse("d5")) })
        assertEquals(CaptureVerdict.FAVORABLE, r.captures.single().verdict)
        // Les Noirs au trait : leur dame est menacée.
        val b = Position.fromFen("4k3/8/8/3q4/8/2N5/8/4K3 b - - 0 1")
        val rb = TacticalScanner.scan(b)
        assertTrue(rb.motifs.any { it.type == MotifType.THREATENED_PIECE && Sq.parse("d5") in it.squares })
        assertTrue(rb.motifs.any { it.type == MotifType.DEFENSE })
    }

    @Test fun staticCaptureOfDefendedPawnIsUnfavorable() {
        val p = Position.fromFen("4k3/2p5/3p4/8/8/8/8/3QK3 w - - 0 1")
        val c = TacticalScanner.scan(p).captures.single()
        assertEquals(CaptureVerdict.UNFAVORABLE, c.verdict)
        assertTrue(c.explanation.contains("reprise"))
        assertTrue(c.consequences.any { it.contains("reprise par") })
    }
}

class OpeningBookTest {
    private val book = OpeningBook.loadDefault()

    @Test fun loadsWholeDatabase() {
        assertTrue(book.all.size > 3500)
        assertEquals("Défense sicilienne", book.families().first())
    }

    @Test fun identifiesSicilianAndRuyLopez() {
        val sic = book.identify(Game.new().playSan("e4").playSan("c5"))
        assertEquals("B20", sic?.eco)
        assertEquals("Défense sicilienne", sic?.family)
        val ruy = book.identify(Pgn.parse("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 *"))
        assertEquals("Partie espagnole", ruy?.family)
        assertTrue(ruy!!.uciMoves.size >= 8)
        assertTrue(book.isBookPrefix(listOf("e2e4", "c7c5")))
        assertFalse(book.isBookPrefix(listOf("e2e4", "c7c5", "e1e2", "a7a6", "e2e1", "a6a5", "h2h4", "h7h5")))
    }

    @Test fun searchIsAccentInsensitive() {
        assertTrue(book.search("defense francaise").isNotEmpty())
        assertTrue(book.search("Najdorf").all { it.family == "Défense sicilienne" })
        assertTrue(book.continuations(listOf("e2e4")).containsKey("c7c5"))
    }

    /** Vérifie que notre lecteur PGN reproduit exactement les coups UCI de toute la base. */
    @Test fun ourPgnParserMatchesEveryOpening() {
        for (o in book.all) {
            val g = Pgn.parse(o.pgn + " *")
            assertEquals(o.originalName, o.uciMoves, g.uciMoves)
        }
    }
}

class PuzzleAndEndgameDataTest {
    @Test fun puzzlesAreLegal() {
        assertTrue(PuzzleSet.puzzles.size >= 25)
        assertEquals(PuzzleSet.puzzles.size, PuzzleSet.puzzles.map { it.id }.toSet().size)
        for (p in PuzzleSet.puzzles) {
            val pos = Position.fromFen(p.fen)
            assertTrue(p.id, pos.validationErrors().isEmpty())
            assertTrue(p.id, p.solution.size % 2 == 1)
            Game.fromUci(pos, p.solution) // lève une exception si un coup est illégal
            assertTrue(p.difficulty in 1..3)
        }
    }

    @Test fun alternativeMateAcceptedOnLastMove() {
        val p = PuzzleSet.puzzles.first { it.id == "baiser" }
        val pos = Position.fromFen(p.fen)
        assertTrue(p.solution[0] in setOf("d4g7", "d4h8"))
        val other = if (p.solution[0] == "d4g7") "d4h8" else "d4g7"
        assertTrue(PuzzleSet.isCorrectMove(p, 0, pos, Move.fromUci(p.solution[0])!!))
        assertTrue(PuzzleSet.isCorrectMove(p, 0, pos, Move.fromUci(other)!!)) // autre mat accepté
        assertFalse(PuzzleSet.isCorrectMove(p, 0, pos, Move.fromUci("d4a7")!!))
    }

    @Test fun endgamesAreLegal() {
        assertTrue(EndgameSet.endgames.size >= 10)
        for (e in EndgameSet.endgames) {
            assertTrue(e.id, Position.fromFen(e.fen).validationErrors().isEmpty())
            assertTrue(e.goal == EndgameSet.WIN || e.goal == EndgameSet.DRAW)
        }
    }
}
