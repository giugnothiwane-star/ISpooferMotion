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

        // Hauteur de référence : la plus grande pièce de l'échiquier (le roi dans tous les jeux usuels).
        val refHeight = cells.indices.filter { occupied[it] }.maxOfOrNull { cells[it].features.height } ?: 0f
        fun rel(f: SquareFeatures) = if (refHeight > 0f) f.height / refHeight else Float.NaN

        // Calibration automatique sur la position initiale.
        val startOrientation = startPositionOrientation(occupied, colors)
        val calibrated = startOrientation != null
        if (calibrated) {
            val initial = Position.initial()
            for (c in cells) {
                val sq = squareOf(c.row, c.col, startOrientation!!)
                val p = initial.pieceAt(sq) ?: continue
                learn(p, c.features, rel(c.features))
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
        val pending = ArrayList<Pending>()
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
            val costs = typeCosts(c.features, rel(c.features), color, all)
            pending += Pending(sq, color, costs, minOf(occConf, 0.4f + 0.6f * colorConf[i]))
        }
        if (pending.isNotEmpty()) {
            val (assigned, corrected) = decodeWithRules(pending)
            if (corrected > 0) warnings += "$corrected pièce(s) ajustée(s) pour respecter les règles (un roi par camp, pions hors des rangées 1 et 8) : vérifiez-les."
            for (p in pending) {
                val (type, conf) = assigned.getValue(p.square)
                results[p.square] = SquareRecognition(p.square, Piece.of(type, p.color), minOf(p.baseConf, conf))
            }
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

    private class Pending(val square: Int, val color: PieceColor, val costs: Map<PieceType, Float>, val baseConf: Float)

    /** Coût (distance) de chaque type de pièce pour une case, d'après les gabarits de la même couleur. */
    private fun typeCosts(f: SquareFeatures, relHeight: Float, color: PieceColor, all: List<PieceTemplate>): Map<PieceType, Float> {
        val out = HashMap<PieceType, Float>()
        if (all.isEmpty()) { PieceType.entries.forEach { out[it] = 1f }; return out }
        val sameColor = all.filter { it.piece.color == color }
        val pool = sameColor.ifEmpty { all }
        for (t in pool) {
            // Distance exacte (même jeu de pièces) ou indépendante de l'échelle (autre jeu) : la meilleure des deux.
            val d = if (!relHeight.isNaN() && !t.relHeight.isNaN()) f.scaleFreeDistanceTo(t, relHeight) else f.distanceTo(t)
            val cur = out[t.piece.type]
            if (cur == null || d < cur) out[t.piece.type] = d
        }
        for (t in PieceType.entries) if (t !in out) out[t] = 2f
        return out
    }

    /** Confiance d'un choix : écart relatif avec le meilleur type concurrent. */
    private fun confidence(costs: Map<PieceType, Float>, chosen: PieceType): Float {
        val c = costs.getValue(chosen)
        val other = costs.filterKeys { it != chosen }.values.minOrNull() ?: return 0.5f
        var conf = if (other <= 0f) 0f else ((other - c) / other * 1.8f).coerceIn(0f, 1f)
        if (c > 0.30f) conf *= 0.6f
        return conf
    }

    /**
     * Choisit le type de chaque pièce en respectant les règles du jeu : exactement un roi par camp,
     * aucun pion sur la 1re ou la 8e rangée, au plus 8 pions par camp. Renvoie aussi le nombre de
     * pièces dont le type a dû être changé par rapport au plus proche gabarit.
     */
    private fun decodeWithRules(items: List<Pending>): Pair<Map<Int, Pair<PieceType, Float>>, Int> {
        val out = HashMap<Int, Pair<PieceType, Float>>()
        var corrected = 0
        for (color in PieceColor.entries) {
            val mine = items.filter { it.color == color }
            if (mine.isEmpty()) continue
            fun backRank(sq: Int) = Sq.rank(sq) == 0 || Sq.rank(sq) == 7
            fun bestType(p: Pending, exclude: Set<PieceType>): PieceType =
                p.costs.filterKeys { it !in exclude && !(it == PieceType.PAWN && backRank(p.square)) }.minByOrNull { it.value }!!.key
            // Le roi : la case pour laquelle « roi » coûte le moins par rapport à la meilleure autre hypothèse.
            val king = mine.minByOrNull { p -> p.costs.getValue(PieceType.KING) - p.costs.filterKeys { it != PieceType.KING }.values.min() }!!
            val choice = HashMap<Int, PieceType>()
            choice[king.square] = PieceType.KING
            for (p in mine) if (p !== king) choice[p.square] = bestType(p, setOf(PieceType.KING))
            // Au plus 8 pions : on reclasse ceux dont l'alternative coûte le moins.
            val pawns = mine.filter { choice[it.square] == PieceType.PAWN }
            if (pawns.size > 8) {
                pawns.sortedBy { p -> p.costs.filterKeys { it != PieceType.PAWN && it != PieceType.KING }.values.min() - p.costs.getValue(PieceType.PAWN) }
                    .take(pawns.size - 8)
                    .forEach { p -> choice[p.square] = bestType(p, setOf(PieceType.KING, PieceType.PAWN)) }
            }
            for (p in mine) {
                val t = choice.getValue(p.square)
                val free = p.costs.minByOrNull { it.value }!!.key
                val conf = if (t == free) confidence(p.costs, t) else { corrected++; 0.45f }
                out[p.square] = t to conf
            }
        }
        return out to corrected
    }

    private fun learn(piece: Piece, f: SquareFeatures, relHeight: Float = Float.NaN) {
        templates.add(PieceTemplate(piece, f.silhouette.copyOf(), f.width, f.height, f.area, relHeight))
        // Les cavaliers regardent à gauche ou à droite selon les jeux : on mémorise aussi le miroir.
        if (piece.type == PieceType.KNIGHT) {
            val g = SquareFeatures.GRID
            val mirrored = FloatArray(g * g) { i -> f.silhouette[(i / g) * g + (g - 1 - i % g)] }
            templates.add(PieceTemplate(piece, mirrored, f.width, f.height, f.area, relHeight))
        }
    }

    /**
     * Apprend à partir d'une correction de l'utilisateur : les cases mal reconnues (ou dont la pièce
     * n'a pas encore de gabarit) enrichissent la banque de gabarits.
     */
    fun learnFromCorrection(image: RgbImage, result: RecognitionResult, correctBoard: Array<Piece?>) {
        require(correctBoard.size == 64) { "Le plateau corrigé doit contenir 64 cases." }
        val cells = cells(image, result.boardRect)
        val refHeight = cells.filter { it.features.occupancy >= OCCUPANCY_THRESHOLD }.maxOfOrNull { it.features.height } ?: 0f
        for (c in cells) {
            val sq = squareOf(c.row, c.col, result.whiteAtBottom)
            val correct = correctBoard[sq] ?: continue
            if (c.features.occupancy < OCCUPANCY_THRESHOLD) continue
            val rel = if (refHeight > 0f) c.features.height / refHeight else Float.NaN
            if (result.squares[sq].piece != correct || !templates.hasTemplatesFor(correct)) learn(correct, c.features, rel)
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

    /**
     * Apprend un jeu de pièces complet (une image de case par pièce, même échelle de dessin) :
     * la hauteur de chaque pièce est mémorisée relativement à la plus grande (le roi), ce qui permet
     * ensuite de reconnaître des jeux de pièces dessinés à une autre échelle.
     */
    fun learnTemplateSet(squares: Map<Piece, RgbImage>) {
        val feats = squares.mapValues { (_, img) ->
            val side = minOf(img.width, img.height).toFloat()
            SquareFeatures.extract(sampleSquare(img, (img.width - side) / 2f, (img.height - side) / 2f, side, SquareFeatures.N))
        }.filterValues { it.occupancy >= OCCUPANCY_THRESHOLD }
        val ref = feats.values.maxOfOrNull { it.height } ?: return
        for ((piece, f) in feats) learn(piece, f, if (ref > 0f) f.height / ref else Float.NaN)
    }

    companion object {
        private const val OCCUPANCY_THRESHOLD = 0.04f
    }
}
