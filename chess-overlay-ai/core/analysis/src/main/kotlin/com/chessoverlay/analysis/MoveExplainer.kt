package com.chessoverlay.analysis

import com.chessoverlay.chess.Move
import com.chessoverlay.chess.MoveKind
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import com.chessoverlay.engine.EngineScore
import com.chessoverlay.engine.PvLine
import kotlin.math.abs

/**
 * Construit l'explication d'un coup à partir de faits vérifiables : évaluation de Stockfish,
 * bilan matériel (SEE), motifs tactiques créés, menaces parées, principes (roque, développement, centre).
 */
object MoveExplainer {

    private val CENTER = setOf(Sq.parse("d4"), Sq.parse("e4"), Sq.parse("d5"), Sq.parse("e5"))

    fun explain(pos: Position, line: PvLine, bestScore: EngineScore?): List<String> {
        val uci = line.pv.firstOrNull() ?: return emptyList()
        val move = Move.fromUci(uci)?.let { pos.normalize(it) } ?: return emptyList()
        return explainMove(pos, move, line.score, bestScore, line.pv)
    }

    fun explainMove(pos: Position, move: Move, score: EngineScore?, bestScore: EngineScore?, pv: List<String> = emptyList()): List<String> {
        val out = mutableListOf<String>()
        val us = pos.sideToMove
        val them = us.opposite
        val d = MoveDescriber.describe(pos, move)
        val next = pos.play(move)

        // 1. Verdict du moteur.
        if (score != null) out += evaluationSentence(score, us)
        if (bestScore != null && score != null) {
            val diff = bestScore.sortValue - score.sortValue
            if (!score.isMate && !bestScore.isMate && diff >= 15) {
                out += "Ce coup est inférieur de ${String.format(java.util.Locale.FRANCE, "%.2f", diff / 100.0)} pion au meilleur coup calculé."
            } else if (bestScore.isMate && !score.isMate) {
                out += "Le meilleur coup mène à un mat forcé ; celui-ci ne le fait pas."
            }
        }

        // 2. Mat, échec.
        if (d.givesMate) {
            out += "${d.sanFrench} donne échec et mat."
            return out
        }

        // 3. Matériel.
        if (d.captured != null) {
            val s = TacticalScanner.see(pos, move)
            val what = "${MoveDescription.article(d.captured).lowercase()}${d.captured.frenchName} en ${Sq.upperName(d.capturedSquare)}"
            out += when {
                s > 0 -> "Il capture $what et l'échange est gagnant (${TacticalScanner.formatCp(s)} à l'échange statique)."
                s == 0 -> "Il capture $what ; l'échange de matériel est équilibré."
                score != null && score.sortValue > -60 -> "Il capture $what en sacrifiant du matériel (${TacticalScanner.formatCp(-s)} à l'échange statique) : Stockfish juge que la suite le compense."
                else -> "Il capture $what, mais la pièce peut être reprise avec perte (${TacticalScanner.formatCp(-s)})."
            }
        } else {
            val s = TacticalScanner.see(pos, move)
            if (s < 0 && score != null && score.sortValue > -60) {
                out += "La pièce se place sur une case attaquée : c'est un sacrifice (${TacticalScanner.formatCp(-s)}) que Stockfish juge correct."
            }
        }
        if (d.promotion != null) out += "Le pion se promeut en ${d.promotion.frenchName}."

        // 4. Motifs tactiques créés.
        val motifs = TacticalScanner.motifsAfterMove(pos, move)
        for (m in motifs) {
            when (m.type) {
                MotifType.CHECK, MotifType.FORK, MotifType.PIN, MotifType.SKEWER,
                MotifType.DISCOVERED_ATTACK, MotifType.MATE_THREAT, MotifType.THREATENED_PIECE -> out += m.description
                else -> {}
            }
        }

        // 5. Menaces adverses parées.
        val threatsBefore = TacticalScanner.threatenedPieces(pos, us).toMap()
        val threatsAfter = TacticalScanner.threatenedPieces(next.withSideToMove(them), us).toMap()
        val movedWasThreatened = threatsBefore.containsKey(move.from)
        if (movedWasThreatened && !threatsAfter.containsKey(move.to) && d.kind != MoveKind.CASTLE_KINGSIDE) {
            out += "Il met à l'abri ${MoveDescription.article(d.piece).lowercase()}${d.piece.frenchName} qui était menacé${if (d.piece.type.feminine) "e" else ""} en ${Sq.upperName(move.from)}."
        }
        for ((sq, _) in threatsBefore) {
            if (sq == move.from) continue
            if (!threatsAfter.containsKey(sq) && next.pieceAt(sq)?.color == us) {
                val p = next.pieceAt(sq)!!
                out += "Il protège ${MoveDescription.article(p).lowercase()}${p.frenchName} en ${Sq.upperName(sq)}, qui était menacé${if (p.type.feminine) "e" else ""}."
            }
        }
        if (!pos.isCheck) {
            val mateBefore = TacticalScanner.mateThreats(pos, them)
            if (mateBefore.isNotEmpty() && TacticalScanner.mateThreats(next, them).isEmpty()) {
                out += "Il pare la menace de mat adverse."
            }
        } else {
            out += "Il répond à l'échec."
        }

        // 6. Principes stratégiques vérifiables.
        when (d.kind) {
            MoveKind.CASTLE_KINGSIDE, MoveKind.CASTLE_QUEENSIDE ->
                out += "Le roque met le roi à l'abri et rapproche la tour du centre."
            else -> {}
        }
        val backRank = if (us == PieceColor.WHITE) 0 else 7
        if (pos.fullmoveNumber <= 15 && d.piece.type in setOf(PieceType.KNIGHT, PieceType.BISHOP) && Sq.rank(move.from) == backRank) {
            out += "Il développe une pièce mineure, conformément aux principes de l'ouverture."
        }
        val centerBefore = CENTER.count { pos.attackers(it, us).isNotEmpty() || pos.pieceAt(it)?.color == us }
        val centerAfter = CENTER.count { next.attackers(it, us).isNotEmpty() || next.pieceAt(it)?.color == us }
        if (centerAfter > centerBefore) {
            val squares = CENTER.filter { next.attackers(it, us).isNotEmpty() || next.pieceAt(it)?.color == us }.joinToString(", ") { Sq.upperName(it) }
            out += "Il renforce le contrôle du centre ($squares)."
        }

        // 7. Réponse attendue.
        if (pv.size >= 2) {
            val reply = MoveDescriber.describeLine(pos, pv.take(2)).getOrNull(1)
            if (reply != null) out += "Réponse adverse attendue selon Stockfish : ${reply.sanFrench} (${reply.headline})."
        }

        if (out.size <= 2 && d.captured == null && motifs.isEmpty()) {
            out += "Coup de placement : il n'y a pas de gain immédiat, son intérêt apparaît dans la suite calculée par le moteur."
        }
        return out.distinct()
    }

    fun evaluationSentence(score: EngineScore, us: PieceColor): String {
        score.mate?.let { m ->
            return if (m > 0) "Stockfish voit un mat forcé en $m coup${if (m > 1) "s" else ""} pour les ${us.frenchSide}."
            else if (m < 0) "Stockfish voit un mat en ${-m} coup${if (-m > 1) "s" else ""} pour l'adversaire : c'est la défense la plus résistante."
            else "La position est mate."
        }
        val cp = score.centipawns ?: 0
        val who = if (cp >= 0) us.frenchSide else us.opposite.frenchSide
        val a = abs(cp)
        val level = when {
            a < 30 -> return "Position équilibrée selon Stockfish (${score.format()} pour le camp au trait)."
            a < 100 -> "léger avantage"
            a < 250 -> "net avantage"
            else -> "avantage décisif"
        }
        return "Après ce coup, Stockfish évalue la position à ${score.forWhite(us == PieceColor.WHITE).format()} (point de vue des Blancs) : $level pour les $who."
    }
}
