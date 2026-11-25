package com.chessoverlay.analysis

import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.San
import com.chessoverlay.engine.AnalysisSnapshot
import com.chessoverlay.engine.ChessEngine
import com.chessoverlay.engine.EngineScore
import com.chessoverlay.engine.SearchLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.exp
import kotlin.math.max

data class AnalysisSettings(val depth: Int = 18, val moveTimeMs: Long? = null, val multiPv: Int = 3)

/** Une variante calculée par Stockfish, enrichie de descriptions et d'explications. */
data class AnalyzedLine(
    val rank: Int,
    /** Évaluation du point de vue du camp au trait. */
    val score: EngineScore,
    val scoreWhite: EngineScore,
    val depth: Int,
    val first: MoveDescription,
    val moves: List<MoveDescription>,
    /** Variante numérotée en notation française. */
    val sanLine: String,
    val explanation: List<String>,
    val motifs: List<TacticalMotif>,
)

data class PositionAnalysis(
    val fen: String,
    val sideToMove: PieceColor,
    val depth: Int,
    val lines: List<AnalyzedLine>,
    val opponentBestReply: MoveDescription?,
    val followUp: List<MoveDescription>,
    val finished: Boolean,
    val nodes: Long,
    val nps: Long,
    /** Non nul si la partie est terminée dans cette position (mat, pat…). */
    val gameOverReason: String?,
) {
    val best: AnalyzedLine? get() = lines.firstOrNull()
}

enum class MoveClassification(val frenchLabel: String) {
    BEST("Meilleur coup"),
    EXCELLENT("Excellent"),
    GOOD("Bon coup"),
    INACCURACY("Imprécision"),
    MISTAKE("Erreur"),
    BLUNDER("Gaffe"),
    BOOK("Théorie"),
    FORCED("Coup forcé"),
}

data class MoveReview(
    val ply: Int,
    val san: String,
    val mover: PieceColor,
    val evalBeforeWhite: EngineScore,
    val evalAfterWhite: EngineScore,
    val bestMoveSan: String?,
    val bestMoveUci: String?,
    val classification: MoveClassification,
    /** Perte de pourcentage de gain (0..100) pour le joueur qui a joué le coup. */
    val winPercentLoss: Double,
    val comment: String,
)

data class GameReview(val moves: List<MoveReview>, val accuracyWhite: Double, val accuracyBlack: Double)

/** Orchestration des analyses Stockfish. */
class AnalysisService(private val engine: ChessEngine) {

    private val explanationCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 200
    }

    fun analyze(pos: Position, settings: AnalysisSettings): Flow<PositionAnalysis> {
        gameOverReason(pos)?.let { reason ->
            return flowOf(PositionAnalysis(pos.toFen(), pos.sideToMove, 0, emptyList(), null, emptyList(), true, 0, 0, reason))
        }
        val limits = SearchLimits(depth = if (settings.moveTimeMs == null) settings.depth else null, moveTimeMs = settings.moveTimeMs, multiPv = settings.multiPv)
        return engine.analyze(pos.toFen(), limits).map { build(pos, it) }
    }

    fun build(pos: Position, snap: AnalysisSnapshot): PositionAnalysis {
        val white = pos.sideToMove == PieceColor.WHITE
        val bestScore = snap.lines.firstOrNull()?.score
        val lines = snap.lines.mapIndexedNotNull { i, l ->
            val moves = MoveDescriber.describeLine(pos, l.pv)
            val first = moves.firstOrNull() ?: return@mapIndexedNotNull null
            val key = "${pos.toFen()}|${first.uci}|${l.score.format()}|${bestScore?.format()}|${l.pv.getOrNull(1)}"
            val explanation = if (snap.finished || i == 0) {
                synchronized(explanationCache) { explanationCache[key] } ?: MoveExplainer.explain(pos, l, if (i == 0) null else bestScore)
                    .also { synchronized(explanationCache) { explanationCache[key] = it } }
            } else emptyList()
            AnalyzedLine(
                rank = i + 1,
                score = l.score,
                scoreWhite = l.score.forWhite(white),
                depth = l.depth,
                first = first,
                moves = moves,
                sanLine = MoveDescriber.numberedLine(pos, moves),
                explanation = explanation,
                motifs = TacticalScanner.motifsAfterMove(pos, first.move, includeMateThreat = snap.finished),
            )
        }
        val best = lines.firstOrNull()
        val top = snap.lines.firstOrNull()
        return PositionAnalysis(
            fen = pos.toFen(),
            sideToMove = pos.sideToMove,
            depth = snap.depth,
            lines = lines,
            opponentBestReply = best?.moves?.getOrNull(1),
            followUp = best?.moves?.drop(2) ?: emptyList(),
            finished = snap.finished,
            nodes = top?.nodes ?: 0,
            nps = top?.nps ?: 0,
            gameOverReason = null,
        )
    }

    /** Évalue chaque capture légale avec Stockfish (recherche restreinte via « searchmoves »). */
    suspend fun evaluateCaptures(pos: Position, depth: Int = 12): List<CaptureInfo> {
        val captures = pos.legalMoves().filter { pos.isCapture(it) }
        if (captures.isEmpty()) return emptyList()
        val fen = pos.toFen()
        val bestSnap = engine.search(fen, SearchLimits(depth = depth))
        val bestScore = bestSnap.best?.score
        val bestUci = bestSnap.bestMove?.move
        val bestSan = bestUci?.let { u -> Move.fromUci(u)?.let { pos.normalize(it) }?.let { San.toSan(pos, it, true) } }
        val out = mutableListOf<CaptureInfo>()
        for (m in captures) {
            val base = TacticalScanner.staticCaptureInfo(pos, m)
            val snap = if (m.uci == bestUci) bestSnap else engine.search(fen, SearchLimits(depth = depth, searchMoves = listOf(m.uci)))
            val score = snap.best?.score
            if (score == null || bestScore == null) {
                out += base.copy(verdict = CaptureVerdict.UNKNOWN); continue
            }
            val delta = max(0, bestScore.sortValue - score.sortValue).let { if (it > 50_000) 99_999 else it }
            val verdict = when {
                score.mate != null && score.mate!! > 0 -> CaptureVerdict.FAVORABLE
                score.mate != null && score.mate!! < 0 -> CaptureVerdict.UNFAVORABLE
                delta <= 25 -> CaptureVerdict.FAVORABLE
                delta <= 80 -> CaptureVerdict.EQUAL
                else -> CaptureVerdict.UNFAVORABLE
            }
            val engineText = when (verdict) {
                CaptureVerdict.FAVORABLE ->
                    if (m.uci == bestUci) "Stockfish confirme : c'est le meilleur coup de la position (${score.format()})."
                    else "Stockfish la juge aussi bonne que le meilleur coup (${score.format()})."
                CaptureVerdict.EQUAL -> "Jouable mais pas optimale : ${score.format()} contre ${bestScore.format()} pour le meilleur coup ($bestSan)."
                CaptureVerdict.UNFAVORABLE -> if (score.mate != null && score.mate!! < 0)
                    "À éviter : après cette capture, Stockfish voit un mat adverse en ${-score.mate!!}."
                else "À éviter : Stockfish évalue la capture à ${score.format()} contre ${bestScore.format()} pour $bestSan."
                CaptureVerdict.UNKNOWN -> ""
            }
            val consequences = base.consequences.toMutableList()
            val pvMoves = MoveDescriber.describeLine(pos, snap.best?.pv ?: emptyList())
            if (pvMoves.size > 1) consequences += "Suite calculée : " + MoveDescriber.numberedLine(pos, pvMoves.take(6))
            out += base.copy(
                engineScore = score,
                deltaVsBest = delta,
                verdict = verdict,
                explanation = base.explanation + " " + engineText,
                consequences = consequences,
            )
        }
        return out.sortedBy { it.deltaVsBest ?: Int.MAX_VALUE }
    }

    /** Revue complète d'une partie : évaluation de chaque position et classement de chaque coup. */
    suspend fun reviewGame(game: Game, depth: Int = 14, onProgress: (Int, Int) -> Unit = { _, _ -> }): GameReview {
        val positions = game.positions
        val total = positions.size
        val evals = ArrayList<EngineScore>(total) // point de vue des Blancs
        val bestMoves = ArrayList<String?>(total)
        engine.newGame()
        for ((i, p) in positions.withIndex()) {
            onProgress(i, total)
            when {
                p.isCheckmate -> { evals += EngineScore(null, 0); bestMoves += null }
                p.legalMoves().isEmpty() || p.isInsufficientMaterial -> { evals += EngineScore.ZERO; bestMoves += null }
                else -> {
                    val snap = engine.search(p.toFen(), SearchLimits(depth = depth))
                    val s = snap.best?.score ?: EngineScore.ZERO
                    evals += s.forWhite(p.sideToMove == PieceColor.WHITE)
                    bestMoves += snap.bestMove?.move
                }
            }
        }
        onProgress(total, total)
        val book = runCatching { OpeningBook.loadDefault() }.getOrNull()
        var inBook = true
        val reviews = mutableListOf<MoveReview>()
        val accW = mutableListOf<Double>(); val accB = mutableListOf<Double>()
        for (i in game.moves.indices) {
            val before = positions[i]
            val after = positions[i + 1]
            val mover = before.sideToMove
            val wpBefore = moverWinPercent(evals[i], before, mover)
            val wpAfter = moverWinPercent(evals[i + 1], after, mover)
            val loss = max(0.0, wpBefore - wpAfter)
            val played = game.moves[i].uci
            val best = bestMoves[i]
            inBook = inBook && book != null && i < 30 && book.isBookPrefix(game.uciMoves.take(i + 1))
            val cls = when {
                inBook -> MoveClassification.BOOK
                before.legalMoves().size == 1 -> MoveClassification.FORCED
                played == best -> MoveClassification.BEST
                loss < 2 -> MoveClassification.EXCELLENT
                loss < 5 -> MoveClassification.GOOD
                loss < 10 -> MoveClassification.INACCURACY
                loss < 20 -> MoveClassification.MISTAKE
                else -> MoveClassification.BLUNDER
            }
            val bestSan = best?.let { u -> Move.fromUci(u)?.let { before.normalize(it) }?.let { San.toSan(before, it, true) } }
            val comment = when (cls) {
                MoveClassification.BOOK -> "Coup théorique de l'ouverture."
                MoveClassification.FORCED -> "Seul coup légal."
                MoveClassification.BEST -> "Le meilleur coup selon Stockfish."
                MoveClassification.EXCELLENT, MoveClassification.GOOD ->
                    "${cls.frenchLabel} : perte de ${fmt(loss)} % de chances de gain." + (bestSan?.let { " Meilleur : $it." } ?: "")
                else -> "${cls.frenchLabel} : perte de ${fmt(loss)} % de chances de gain. Stockfish recommandait $bestSan."
            }
            val acc = (103.1668 * exp(-0.04354 * loss) - 3.1669).coerceIn(0.0, 100.0)
            if (mover == PieceColor.WHITE) accW += acc else accB += acc
            reviews += MoveReview(
                ply = i + 1, san = San.toSan(before, game.moves[i], true), mover = mover,
                evalBeforeWhite = evals[i], evalAfterWhite = evals[i + 1],
                bestMoveSan = bestSan, bestMoveUci = best, classification = cls,
                winPercentLoss = loss, comment = comment,
            )
        }
        return GameReview(reviews, accuracy(accW), accuracy(accB))
    }

    private fun fmt(d: Double) = String.format(java.util.Locale.FRANCE, "%.0f", d)

    /** Moyenne de la moyenne arithmétique et de la moyenne harmonique (approche Lichess, sans pondération). */
    private fun accuracy(values: List<Double>): Double {
        if (values.isEmpty()) return 100.0
        val mean = values.average()
        val harmonic = values.size / values.sumOf { 1.0 / it.coerceAtLeast(1.0) }
        return (mean + harmonic) / 2
    }

    private fun moverWinPercent(scoreWhite: EngineScore, pos: Position, mover: PieceColor): Double {
        val whiteWp = if (scoreWhite.mate == 0) {
            if (pos.isCheckmate) (if (pos.sideToMove == PieceColor.WHITE) 0.0 else 100.0) else 50.0
        } else scoreWhite.winPercent()
        return if (mover == PieceColor.WHITE) whiteWp else 100.0 - whiteWp
    }

    companion object {
        fun gameOverReason(pos: Position): String? = when {
            pos.isCheckmate -> "Échec et mat : les ${pos.sideToMove.opposite.frenchSide} ont gagné."
            pos.isStalemate -> "Pat : la partie est nulle."
            pos.isInsufficientMaterial -> "Nulle : matériel insuffisant pour mater."
            else -> null
        }
    }
}
