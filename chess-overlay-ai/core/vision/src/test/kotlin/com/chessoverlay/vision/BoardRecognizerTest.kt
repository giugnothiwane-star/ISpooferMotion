package com.chessoverlay.vision

import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

class BoardRecognizerTest {

    private val dir = File(System.getProperty("fixtures.dir") ?: "src/test/resources")

    private data class Fixture(val name: String, val fen: String, val x: Int, val y: Int, val size: Int, val whiteBottom: Boolean)

    private val fixtures: Map<String, Fixture> = File(dir, "fixtures.txt").readLines().filter { it.isNotBlank() }.associate { line ->
        val p = line.split('|')
        p[0] to Fixture(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toBoolean())
    }

    private fun load(name: String): RgbImage {
        val img = ImageIO.read(File(dir, "$name.png"))
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return RgbImage(img.width, img.height, px)
    }

    /** Planche de gabarits : wK wQ wR wB wN wP bK bQ bR bB bN bP, sur case claire puis sombre. */
    private fun learnSprite(recognizer: BoardRecognizer, set: String) {
        val sheet = load("templates_$set")
        val s = sheet.height / 2
        val order = "KQRBNPkqrbnp"
        for (row in 0 until 2) for ((i, c) in order.withIndex()) {
            recognizer.learnTemplate(Piece.fromFenChar(c)!!, sheet.crop(i * s, row * s, s, s))
        }
    }

    private fun assertBoard(fixture: String, result: RecognitionResult) {
        val expected = Position.fromFen(fixtures.getValue(fixture).fen)
        val errors = (0 until 64).filter { expected.pieceAt(it) != result.squares[it].piece }.map {
            "${Sq.name(it)}: attendu ${expected.pieceAt(it)?.fenChar ?: '.'}, reconnu ${result.squares[it].piece?.fenChar ?: '.'}"
        }
        if (errors.isNotEmpty()) fail("$fixture : ${errors.size} erreur(s) : $errors")
    }

    @Test fun detectsBoardFrameOnAllFixtures() {
        val r = BoardRecognizer(PieceTemplateStore())
        for (f in fixtures.values) {
            val rect = r.detectBoard(load(f.name))
            assertNotNull("Plateau non détecté : ${f.name}", rect)
            rect!!
            assertTrue("${f.name} gauche ${rect.left} ≠ ${f.x}", abs(rect.left - f.x) <= 3)
            assertTrue("${f.name} haut ${rect.top} ≠ ${f.y}", abs(rect.top - f.y) <= 3)
            assertTrue("${f.name} taille ${rect.size} ≠ ${f.size}", abs(rect.size - f.size) <= 4)
        }
    }

    @Test fun recognizesWithRenderedTemplatesAndHighlight() {
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "cburnett")
        val res = r.recognize(load("lichess_cburnett_mid"))
        assertBoard("lichess_cburnett_mid", res)
        assertTrue(res.whiteAtBottom)
        assertFalse(res.calibratedFromStartPosition)
        assertEquals(Sq.parse("d2") to Sq.parse("d3"), res.lastMove)
        assertEquals(PieceColor.BLACK, res.suggestedSideToMove)
        assertEquals(Sq.NONE, res.suggestedEpSquare)
        assertTrue("confiance ${res.overallConfidence}", res.overallConfidence > 0.8f)
    }

    @Test fun recognizesBlackAtBottom() {
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "cburnett")
        val res = r.recognize(load("lichess_cburnett_black"))
        assertFalse(res.whiteAtBottom)
        assertBoard("lichess_cburnett_black", res)
        assertEquals(PieceColor.WHITE, res.suggestedSideToMove)
        assertEquals(Sq.parse("g8") to Sq.parse("f6"), res.lastMove)
    }

    @Test fun forcedOrientationIsRespected() {
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "cburnett")
        val res = r.recognize(load("lichess_cburnett_mid"), forceWhiteAtBottom = false)
        assertFalse(res.whiteAtBottom)
    }

    @Test fun calibratesFromStartPositionThenRecognizesOtherPositions() {
        val store = PieceTemplateStore()
        val r = BoardRecognizer(store)
        val start = r.recognize(load("lichess_cburnett_start"))
        assertTrue(start.calibratedFromStartPosition)
        assertTrue(start.whiteAtBottom)
        assertBoard("lichess_cburnett_start", start)
        for (c in "KQRBNPkqrbnp") assertTrue(store.hasTemplatesFor(Piece.fromFenChar(c)!!))
        assertBoard("lichess_cburnett_mid", r.recognize(load("lichess_cburnett_mid")))
        assertBoard("lichess_cburnett_black", r.recognize(load("lichess_cburnett_black")))
    }

    @Test fun chessComStyleWithMeridaAfterCalibration() {
        val r = BoardRecognizer(PieceTemplateStore())
        assertTrue(r.recognize(load("chesscom_merida_start")).calibratedFromStartPosition)
        val ep = r.recognize(load("chesscom_merida_ep"))
        assertBoard("chesscom_merida_ep", ep)
        assertEquals(PieceColor.WHITE, ep.suggestedSideToMove)
        assertEquals(Sq.parse("d6"), ep.suggestedEpSquare)
        val pos = ep.toPosition(PieceColor.WHITE)
        assertEquals(
            fixtures.getValue("chesscom_merida_ep").fen.split(' ').take(4),
            pos.toFen().split(' ').take(4),
        )
        val mid = r.recognize(load("chesscom_merida_mid"))
        assertBoard("chesscom_merida_mid", mid)
        assertNull(mid.suggestedSideToMove)
        assertTrue(mid.warnings.any { it.contains("trait") })
        // Roques déduits du placement : aucun roi sur sa case d'origine.
        assertEquals("-", mid.toPosition(PieceColor.WHITE).castling.toFen())
    }

    @Test fun meridaRenderedTemplates() {
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "merida")
        assertBoard("chesscom_merida_mid", r.recognize(load("chesscom_merida_mid")))
        assertBoard("chesscom_merida_ep", r.recognize(load("chesscom_merida_ep")))
    }

    @Test fun learnsFromUserCorrection() {
        // Gabarits d'un autre jeu de pièces : on corrige puis on vérifie que la correction est apprise.
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "cburnett")
        val img = load("chesscom_merida_mid")
        val first = r.recognize(img)
        val correct = Array<Piece?>(64) { Position.fromFen(fixtures.getValue("chesscom_merida_mid").fen).pieceAt(it) }
        r.learnFromCorrection(img, first, correct)
        assertBoard("chesscom_merida_mid", r.recognize(img))
    }

    @Test fun templateSerializationRoundTrip() {
        val store = PieceTemplateStore()
        val r = BoardRecognizer(store)
        r.recognize(load("lichess_cburnett_start"))
        val text = store.serialize()
        val copy = PieceTemplateStore.deserialize(text)
        assertEquals(store.size, copy.size)
        assertTrue(copy.size >= 12)
        val res = BoardRecognizer(copy).recognize(load("lichess_cburnett_mid"))
        assertBoard("lichess_cburnett_mid", res)
        assertEquals(0, PieceTemplateStore.deserialize("n'importe quoi").size)
        copy.clear()
        assertEquals(0, copy.size)
    }

    @Test fun noBoardGivesFrenchError() {
        val r = BoardRecognizer(PieceTemplateStore())
        assertNull(r.detectBoard(load("no_board")))
        try {
            r.recognize(load("no_board"))
            fail("Une exception était attendue")
        } catch (e: RecognitionException) {
            assertTrue(e.message!!.contains("Aucun échiquier"))
        }
    }

    @Test fun noTemplatesWarnsAndLowConfidence() {
        val res = BoardRecognizer(PieceTemplateStore()).recognize(load("lichess_cburnett_mid"))
        assertTrue(res.warnings.any { it.contains("gabarit") })
        assertTrue(res.lowConfidenceSquares().isNotEmpty())
        // L'occupation et les couleurs restent correctes même sans gabarit.
        val expected = Position.fromFen(fixtures.getValue("lichess_cburnett_mid").fen)
        for (sq in 0 until 64) assertEquals(expected.pieceAt(sq)?.color, res.squares[sq].piece?.color)
    }

    @Test fun phoneResolutionIsFastAndScaleInvariant() {
        // Agrandissement ×2,67 (≈ 1440×3120, résolution du Galaxy S23 Ultra).
        val src = load("lichess_cburnett_mid")
        val w = 1440; val h = src.height * w / src.width
        val px = IntArray(w * h) { i -> src[(i % w) * src.width / w, (i / w) * src.height / h] }
        val big = RgbImage(w, h, px)
        val r = BoardRecognizer(PieceTemplateStore())
        learnSprite(r, "cburnett")
        r.recognize(big) // préchauffage JIT
        val t0 = System.nanoTime()
        val res = r.recognize(big)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertBoard("lichess_cburnett_mid", res)
        val f = fixtures.getValue("lichess_cburnett_mid")
        val scale = w.toFloat() / src.width
        assertTrue(abs(res.boardRect.left - f.x * scale) <= 6 && abs(res.boardRect.size - f.size * scale) <= 8)
        assertTrue("Reconnaissance trop lente : $ms ms", ms < 1000)
    }
}
