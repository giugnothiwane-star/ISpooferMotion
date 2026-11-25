package com.chessoverlay.vision

import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Captures au format téléphone (1080 × 2316, échiquier pleine largeur, coordonnées dans les cases,
 * dernier coup surligné, barres d'interface), reconnues avec la banque de gabarits livrée par défaut.
 */
class PhoneScreenshotTest {
    private val dir = File(System.getProperty("fixtures.dir"))
    private fun load(name: String): RgbImage {
        val img = ImageIO.read(File(dir, "$name.png")); val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width); return RgbImage(img.width, img.height, px)
    }

    @Test fun defaultTemplatesAreBundled() {
        val store = PieceTemplateStore.loadDefault()
        assertTrue("banque par défaut trop petite : ${store.size}", store.size >= 150)
        for (c in PieceColor.entries) for (t in PieceType.entries) assertTrue(store.hasTemplatesFor(Piece.of(t, c)))
    }

    private fun check(name: String, fen: String, whiteAtBottom: Boolean) {
        val r = BoardRecognizer(PieceTemplateStore.loadDefault()).recognize(load(name))
        val exp = Position.fromFen(fen)
        assertEquals(0f, r.boardRect.left, 3f)
        assertEquals(1080f, r.boardRect.size, 6f)
        assertEquals(whiteAtBottom, r.whiteAtBottom)
        assertEquals(exp.sideToMove, r.suggestedSideToMove)
        val got = r.toPosition(exp.sideToMove)
        assertEquals(fen.substringBefore(' '), got.toFen().substringBefore(' '))
        assertTrue(got.validationErrors().isEmpty())
    }

    @Test fun chessComLayoutMerida() =
        check("phone_chesscom_merida", "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R b KQkq - 0 4", true)

    @Test fun lichessLayoutBlackAtBottom() =
        check("phone_lichess_cburnett_black", "r2q1rk1/ppp2ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPP2PPP/R2Q1RK1 w - - 4 8", false)

    @Test fun templateFormatV1StillReadable() {
        val v1 = "chess-overlay-templates v1\nK;0.5;0.8;0.3;" + "00".repeat(256) + "\n"
        assertEquals(1, PieceTemplateStore.deserialize(v1).size)
        val v2 = PieceTemplateStore.loadDefault()
        assertEquals(v2.size, PieceTemplateStore.deserialize(v2.serialize()).size)
    }
}
