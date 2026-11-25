package com.chessoverlay.vision

import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import kotlin.math.abs

/**
 * Reconnaissance d'une position à partir d'une capture d'écran d'un échiquier numérique.
 *
 * Étapes : localisation du plateau ([BoardDetector]) → découpage en 64 cases → estimation du fond de
 * chaque case → occupation (pixels s'écartant du fond) → couleur des pièces (2-moyennes sur la
 * luminance) → type par plus proche voisin parmi les gabarits de [templates] → orientation, trait et
 * prise en passant déduits du placement et du surlignage du dernier coup.
 *
 * Le résultat doit toujours être présenté à l'utilisateur pour vérification avant toute analyse.
 */
class BoardRecognizer(val templates: PieceTemplateStore) {

    private class Cell(val row: Int, val col: Int, val px: IntArray, val features: SquareFeatures)

    fun detectBoard(image: RgbImage): BoardRect? = BoardDetector.detect(image)?.rect

    private fun cells(image: RgbImage, rect: BoardRect): List<Cell> {
        val s = rect.squareSize
        val n = SquareFeatures.N
        return (0 until 64).map { i ->
            val row = i / 8; val col = i % 8
            val px = sampleSquare(image, rect.left + col * s, rect.top + row * s, s, n)
            Cell(row, col, px, SquareFeatures.extract(px))
        }
    }

    private fun squareOf(row: Int, col: Int, whiteAtBottom: Boolean): Int =
        if (whiteAtBottom) Sq.of(col, 7 - row) else Sq.of(7 - col, row)

    fun recognize(image: RgbImage, hint: BoardRect? = null, forceWhiteAtBottom: Boolean? = null): RecognitionResult {
        val rect = hint ?: detectBoard(image)
            ?: throw RecognitionException(
                "Aucun échiquier n'a été détecté sur l'image. Vérifiez que le plateau est entièrement visible, " +
                    "non masqué et affiché à plat (capture d'écran d'une application ou d'un site d'échecs)."
            )
        if (rect.size < 64f) throw RecognitionException("L'échiquier détecté est trop petit pour être reconnu de façon fiable.")
        val warnings = mutableListOf<String>()
        val cells = cells(image, rect)

        // Couleur de référence des deux types de cases et cases surlignées (dernier coup).
        val parityColor = IntArray(2) { p ->
            val bgs = cells.filter { (it.row + it.col) % 2 == p }.map { it.features.background }
            bgs.maxByOrNull { c -> bgs.count { Rgb.dist2(it, c) <= 18 * 18 } } ?: 0
        }
        val highlighted = cells.filter { Rgb.dist2(it.features.background, parityColor[(it.row + it.col) % 2]) > 18 * 18 }

        // Occupation.
        val occupied = cells.map { it.features.occupancy >= OCCUPANCY_THRESHOLD }

        // Couleur des pièces : 2-moyennes sur la luminance moyenne des pixels de pièce.
        val lums = cells.indices.filter { occupied[it] }.map { cells[it].features.meanLum }
        val colorSplit = splitColors(lums)
        val colors = arrayOfNulls<PieceColor>(64)
        val colorConf = FloatArray(64) { 1f }
        for (i in cells.indices) if (occupied[i]) {
            val l = cells[i].features.meanLum
            colors[i] = if (l >= colorSplit.threshold) PieceColor.WHITE else PieceColor.BLACK
            colorConf[i] = (abs(l - colorSplit.threshold) / colorSplit.halfGap).coerceIn(0f, 1f)
        }

        // Calibration automatique sur la position initiale.
        val startOrientation = startPositionOrientation(occupied, colors)
        val calibrated = startOrientation != null
        if (calibrated) {
            val initial = Position.initial()
            for (c in cells) {
                val sq = squareOf(c.row, c.col, startOrientation!!)
                val p = initial.pieceAt(sq) ?: continue
                learn(p, c.features)
            }
        }

        // Orientation.
        val whiteAtBottom = forceWhiteAtBottom ?: startOrientation ?: run {
            val whiteRows = cells.indices.filter { colors[it] == PieceColor.WHITE }.map { cells[it].row }
            val blackRows = cells.indices.filter { colors[it] == PieceColor.BLACK }.map { cells[it].row }
            if (whiteRows.isEmpty() || blackRows.isEmpty()) {
                warnings += "Orientation impossible à déterminer : blancs supposés en bas. Vérifiez et retournez l'échiquier si besoin."
                true
            } else {
                val diff = whiteRows.average() - blackRows.average()
                if (abs(diff) < 1.0) warnings += "Orientation incertaine (déduite de la position moyenne des pièces) : vérifiez-la."
                diff >= 0
            }
        }

        // Types de pièces.
        val all = templates.all()
        if (all.isEmpty()) {
            warnings += "Aucun gabarit de pièces n'est disponible : les types de pièces n'ont pas pu être identifiés. " +
                "Importez une capture de la position initiale pour calibrer ce jeu de pièces."
        }
        val results = arrayOfNulls<SquareRecognition>(64)
        for ((i, c) in cells.withIndex()) {
            val sq = squareOf(c.row, c.col, whiteAtBottom)
            val occ = c.features.occupancy
            if (!occupied[i]) {
                val conf = if (occ < 0.01f) 1f else (1f - occ / OCCUPANCY_THRESHOLD).coerceIn(0.3f, 1f)
                results[sq] = SquareRecognition(sq, null, conf)
                continue
            }
            val color = colors[i]!!
            val occConf = (0.5f + (occ - OCCUPANCY_THRESHOLD) / OCCUPANCY_THRESHOLD).coerceIn(0.3f, 1f)
            if (calibrated) {
                results[sq] = SquareRecognition(sq, Position.initial().pieceAt(sq), 0.97f.coerceAtMost(occConf.coerceAtLeast(0.9f)))
                continue
            }
            val (type, typeConf) = classifyType(c.features, color, all)
            val conf = minOf(occConf, 0.4f + 0.6f * colorConf[i], typeConf)
            results[sq] = SquareRecognition(sq, Piece.of(type, color), conf)
        }
        val squares = results.map { it!! }

        // Dernier coup, trait et prise en passant.
        var lastMove: Pair<Int, Int>? = null
        var side: PieceColor? = null
        var ep = Sq.NONE
        if (highlighted.size == 2) {
            val sqs = highlighted.map { squareOf(it.row, it.col, whiteAtBottom) }
            val occ = sqs.filter { squares[it].piece != null }
            if (occ.size == 1) {
                val to = occ[0]
                val from = sqs.first { it != to }
                lastMove = from to to
                val mover = squares[to].piece!!
                side = mover.color.opposite
                if (mover.type == PieceType.PAWN && Sq.file(from) == Sq.file(to) && abs(Sq.rank(from) - Sq.rank(to)) == 2) {
                    ep = (from + to) / 2
                }
            }
        } else if (highlighted.size > 2) {
            warnings += "Plusieurs cases surlignées : le dernier coup et le trait n'ont pas pu être déduits."
        }
        if (side == null) warnings += "Le camp au trait n'a pas pu être déduit de l'image : indiquez-le avant l'analyse."

        // Contrôles de cohérence.
        for (color in PieceColor.entries) {
            val kings = squares.count { it.piece == Piece.of(PieceType.KING, color) }
            val name = if (color == PieceColor.WHITE) "blanc" else "noir"
            if (kings == 0) warnings += "Aucun roi $name reconnu."
            if (kings > 1) warnings += "$kings rois ${name}s reconnus : au moins une pièce est mal identifiée."
        }
        val low = squares.count { it.confidence < 0.6f }
        if (low > 0) warnings += "$low case(s) reconnue(s) avec une confiance faible : vérifiez-les (elles sont signalées)."

        return RecognitionResult(
            boardRect = rect,
            whiteAtBottom = whiteAtBottom,
            squares = squares,
            suggestedSideToMove = side,
            lastMove = lastMove,
            suggestedEpSquare = ep,
            overallConfidence = squares.map { it.confidence }.average().toFloat(),
            warnings = warnings,
            calibratedFromStartPosition = calibrated,
        )
    }

    private class ColorSplit(val threshold: Float, val halfGap: Float)

    private fun splitColors(values: List<Float>): ColorSplit {
        if (values.isEmpty()) return ColorSplit(128f, 64f)
        val mn = values.min(); val mx = values.max()
        if (mx - mn < 60f) return ColorSplit(128f, 64f)
        var t = (mn + mx) / 2
        repeat(20) {
            val lo = values.filter { it < t }; val hi = values.filter { it >= t }
            if (lo.isEmpty() || hi.isEmpty()) return@repeat
            t = (lo.average().toFloat() + hi.average().toFloat()) / 2
        }
        val lo = values.filter { it < t }.average().toFloat()
        val hi = values.filter { it >= t }.average().toFloat()
        return ColorSplit(t, ((hi - lo) / 2).coerceAtLeast(20f))
    }

    /** Si l'occupation correspond à la position initiale, renvoie l'orientation (true = blancs en bas). */
    private fun startPositionOrientation(occupied: List<Boolean>, colors: Array<PieceColor?>): Boolean? {
        fun rowColor(row: Int): PieceColor? {
            val cs = (0 until 8).map { colors[row * 8 + it] }
            if ((0 until 8).any { !occupied[row * 8 + it] }) return null
            return if (cs.all { it == cs[0] }) cs[0] else null
        }
        for (row in 2..5) if ((0 until 8).any { occupied[row * 8 + it] }) return null
        val top = rowColor(0) ?: return null
        if (rowColor(1) != top) return null
        val bottom = rowColor(7) ?: return null
        if (rowColor(6) != bottom || bottom == top) return null
        return bottom == PieceColor.WHITE
    }

    private fun classifyType(f: SquareFeatures, color: PieceColor, all: List<PieceTemplate>): Pair<PieceType, Float> {
        if (all.isEmpty()) return PieceType.PAWN to 0.05f
        val sameColor = all.filter { it.piece.color == color }
        val pool = sameColor.ifEmpty { all }
        val bestByType = HashMap<PieceType, Float>()
        for (t in pool) {
            val d = f.distanceTo(t)
            val cur = bestByType[t.piece.type]
            if (cur == null || d < cur) bestByType[t.piece.type] = d
        }
        val sorted = bestByType.entries.sortedBy { it.value }
        val best = sorted[0]
        val second = sorted.getOrNull(1)?.value
        var conf = if (second == null) 0.5f else ((second - best.value) / second * 1.8f).coerceIn(0f, 1f)
        if (best.value > 0.30f) conf *= 0.6f
        if (sameColor.isEmpty()) conf *= 0.8f
        return best.key to conf
    }

    private fun learn(piece: Piece, f: SquareFeatures) {
        templates.add(PieceTemplate(piece, f.silhouette.copyOf(), f.width, f.height, f.area))
    }

    /**
     * Apprend à partir d'une correction de l'utilisateur : les cases mal reconnues (ou dont la pièce
     * n'a pas encore de gabarit) enrichissent la banque de gabarits.
     */
    fun learnFromCorrection(image: RgbImage, result: RecognitionResult, correctBoard: Array<Piece?>) {
        require(correctBoard.size == 64) { "Le plateau corrigé doit contenir 64 cases." }
        val cells = cells(image, result.boardRect)
        for (c in cells) {
            val sq = squareOf(c.row, c.col, result.whiteAtBottom)
            val correct = correctBoard[sq] ?: continue
            if (c.features.occupancy < OCCUPANCY_THRESHOLD) continue
            if (result.squares[sq].piece != correct || !templates.hasTemplatesFor(correct)) learn(correct, c.features)
        }
    }

    /** Apprend un gabarit à partir de l'image d'une case contenant la pièce (fond uni de case). */
    fun learnTemplate(piece: Piece, squareImage: RgbImage) {
        val side = minOf(squareImage.width, squareImage.height).toFloat()
        val px = sampleSquare(
            squareImage, (squareImage.width - side) / 2f, (squareImage.height - side) / 2f, side, SquareFeatures.N,
        )
        val f = SquareFeatures.extract(px)
        if (f.occupancy >= OCCUPANCY_THRESHOLD) learn(piece, f)
    }

    companion object {
        private const val OCCUPANCY_THRESHOLD = 0.04f
    }
}
