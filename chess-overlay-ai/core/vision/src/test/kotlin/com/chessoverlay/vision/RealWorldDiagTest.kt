package com.chessoverlay.vision

import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.Position
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Outil de diagnostic (désactivé sans -Ddiag.dir) : évalue la reconnaissance sur des jeux de pièces non appris. */
class RealWorldDiagTest {
    private val dir = File(System.getProperty("diag.dir") ?: "/nonexistent")
    private fun load(f: File): RgbImage {
        val img = ImageIO.read(f); val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width); return RgbImage(img.width, img.height, px)
    }
    private val order = listOf("wK", "wQ", "wR", "wB", "wN", "wP", "bK", "bQ", "bR", "bB", "bN", "bP")

    private fun store(refs: File): PieceTemplateStore {
        val r = BoardRecognizer(PieceTemplateStore())
        for (f in refs.listFiles()!!.filter { it.name.endsWith(".png") }.sorted()) {
            val strip = load(f); val s = strip.height
            r.learnTemplateSet(order.withIndex().associate { (i, n) ->
                Piece.fromFenChar(if (n[0] == 'w') n[1] else n[1].lowercaseChar())!! to strip.crop(i * s, 0, s, s)
            })
        }
        return r.templates
    }

    @Test fun sweep() {
        assumeTrue(dir.exists() && System.getProperty("sweep") != null)
        val st = store(File(dir, "../refs"))
        val cases = File(dir, "cases.txt").readLines().filter { it.isNotBlank() }.map { it.split('|') }
        val imgs = cases.associate { it[0] to load(File(dir, "${it[0]}.png")) }
        val res = mutableListOf<Pair<Int, String>>()
        for (tw in listOf(1f, 1.4f, 2f)) for (bw in listOf(0.2f, 0.4f, 1f)) for (aw in listOf(0.3f, 0.6f, 1.2f)) for (fw in listOf(0f, 0.8f)) for (rw in listOf(0.7f, 1.4f, 2.5f)) {
            SquareFeatures.topWeight = tw; SquareFeatures.bottomWeight = bw; SquareFeatures.aspectWeight = aw; SquareFeatures.fillWeight = fw; SquareFeatures.relHeightWeight = rw
            var wrong = 0
            for (c in cases) {
                val rr = BoardRecognizer(PieceTemplateStore.deserialize(st.serialize())).recognize(imgs.getValue(c[0]))
                val exp = Position.fromFen(c[1]); val got = rr.toPosition(exp.sideToMove)
                wrong += (0 until 64).count { got.pieceAt(it) != exp.pieceAt(it) }
            }
            res += wrong to "tw=$tw bw=$bw aw=$aw fw=$fw rw=$rw"
        }
        res.sortedBy { it.first }.take(12).forEach { println("DIAG SWEEP ${it.first} ${it.second}") }
    }

    @Test fun diag() {
        assumeTrue(dir.exists())
        val st = store(File(dir, "../refs"))
        System.getProperty("templates.out")?.let { File(it).writeText(st.serialize()); println("DIAG wrote ${st.size} templates") }
        var totalWrong = 0
        for (line in File(dir, "cases.txt").readLines().filter { it.isNotBlank() }) {
            val (name, fen, _) = line.split('|')
            val r = BoardRecognizer(PieceTemplateStore.deserialize(st.serialize()))
            val rr = r.recognize(load(File(dir, "$name.png")))
            val got = rr.toPosition(Position.fromFen(fen).sideToMove)
            val wrong = (0 until 64).count { got.pieceAt(it) != Position.fromFen(fen).pieceAt(it) }
            totalWrong += wrong
            println("DIAG $name wrong=$wrong valid=${got.validationErrors().isEmpty()} low=${rr.lowConfidenceSquares().size}")
            println("DIAG   exp=${fen.substringBefore(' ')}\nDIAG   got=${got.toFen().substringBefore(' ')}")
        }
        println("DIAG TOTAL wrong=$totalWrong")
    }
}
