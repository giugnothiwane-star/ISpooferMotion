package com.chessoverlay.analysis

import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import com.chessoverlay.engine.EngineScore

enum class CaptureVerdict(val frenchLabel: String) {
    FAVORABLE("Favorable"),
    EQUAL("Équilibrée"),
    UNFAVORABLE("Défavorable"),
    UNKNOWN("Indéterminée"),
}

/** Évaluation d'une capture possible. */
data class CaptureInfo(
    val description: MoveDescription,
    /** Bilan de l'échange statique (SEE) en centipions pour le camp qui capture. */
    val see: Int,
    /** Évaluation moteur après la capture, du point de vue du camp qui capture (null si non calculée). */
    val engineScore: EngineScore?,
    /** Écart (centipions) avec le meilleur coup du moteur ; 0 = la capture est le meilleur coup. */
    val deltaVsBest: Int?,
    val verdict: CaptureVerdict,
    val explanation: String,
    val consequences: List<String>,
)

enum class MotifType(val frenchLabel: String) {
    HANGING_PIECE("Pièce non protégée"),
    THREATENED_PIECE("Pièce menacée"),
    FORK("Fourchette"),
    PIN("Clouage"),
    SKEWER("Enfilade"),
    DISCOVERED_ATTACK("Attaque découverte"),
    CHECK("Échec"),
    MATE_THREAT("Menace de mat"),
    MATE_IN_ONE("Mat en un coup"),
    DEFENSE("Défense"),
}

data class TacticalMotif(
    val type: MotifType,
    val title: String,
    val description: String,
    val squares: List<Int>,
    val move: Move? = null,
)

data class TacticalReport(val captures: List<CaptureInfo>, val motifs: List<TacticalMotif>)

/** Détection statique (sans moteur) des captures et des motifs tactiques, à partir des règles du jeu. */
object TacticalScanner {

    private fun value(t: PieceType): Int = if (t == PieceType.KING) 20000 else t.value

    // ------------------------------------------------------------------ SEE

    /**
     * Échange statique (Static Exchange Evaluation) sur la case d'arrivée de [move] : gain matériel net
     * attendu pour le camp qui joue si chaque camp reprend avec sa pièce la moins précieuse.
     * Les clouages ne sont pas pris en compte (estimation classique).
     */
    fun see(pos: Position, move: Move): Int {
        val board = pos.boardCopy()
        val mover = board[move.from] ?: return 0
        val target = move.to
        val gain = IntArray(40)
        val capSq = pos.capturedSquare(move)
        gain[0] = if (capSq >= 0) value(board[capSq]!!.type) else 0
        var onTarget = value(mover.type)
        val promo = move.promotion
        if (promo != null) {
            gain[0] += promo.value - PieceType.PAWN.value
            onTarget = promo.value
        }
        if (capSq >= 0) board[capSq] = null
        board[move.from] = null
        board[target] = if (promo != null) Piece.of(promo, mover.color) else mover
        var side = mover.color.opposite
        var d = 0
        while (d < 38) {
            val attackers = BoardOps.attackers(board, target, side)
            if (attackers.isEmpty()) break
            val from = attackers.minByOrNull { value(board[it]!!.type) }!!
            val attacker = board[from]!!
            if (attacker.type == PieceType.KING) {
                // Le roi ne peut pas reprendre sur une case encore défendue.
                val sim = board.copyOf().also { it[from] = null; it[target] = attacker }
                if (BoardOps.attackers(sim, target, side.opposite).isNotEmpty()) break
            }
            d++
            gain[d] = onTarget - gain[d - 1]
            onTarget = value(attacker.type)
            board[from] = null
            board[target] = attacker
            side = side.opposite
        }
        while (d > 0) {
            gain[d - 1] = -maxOf(-gain[d - 1], gain[d])
            d--
        }
        return gain[0]
    }

    /** Meilleure capture de l'adversaire de [color] sur la pièce en [sq] (gain SEE, >0 = pièce en danger). */
    private fun threatOn(pos: Position, sq: Int, color: PieceColor): Int {
        val enemyView = if (pos.sideToMove == color) pos.withSideToMove(color.opposite) else pos
        var best = Int.MIN_VALUE
        for (a in enemyView.attackers(sq, color.opposite)) {
            val promo = if (enemyView.pieceAt(a)?.type == PieceType.PAWN && (Sq.rank(sq) == 0 || Sq.rank(sq) == 7)) PieceType.QUEEN else null
            val m = Move(a, sq, promo)
            best = maxOf(best, see(enemyView, m))
        }
        return best
    }

    /** Pièces de [color] que l'adversaire pourrait gagner (case → gain SEE). */
    fun threatenedPieces(pos: Position, color: PieceColor): List<Pair<Int, Int>> =
        pos.pieces(color).filter { it.second.type != PieceType.KING }
            .mapNotNull { (sq, _) -> threatOn(pos, sq, color).takeIf { it > 0 }?.let { sq to it } }
            .sortedByDescending { it.second }

    fun isDefended(pos: Position, sq: Int): Boolean {
        val p = pos.pieceAt(sq) ?: return false
        return pos.attackers(sq, p.color).isNotEmpty()
    }

    /** Coups légaux donnant le mat immédiatement. */
    fun matingMoves(pos: Position): List<Move> = pos.legalMoves().filter { pos.play(it).isCheckmate }

    /** Mats en un coup dont disposerait [color] s'il avait le trait. */
    fun mateThreats(pos: Position, color: PieceColor): List<Move> {
        val view = if (pos.sideToMove == color) pos else pos.withSideToMove(color)
        // Si le camp adverse est en échec dans la vue « coup nul », la menace n'a pas de sens.
        val oppKing = view.kingSquare(color.opposite)
        if (oppKing < 0) return emptyList()
        if (view.isAttacked(oppKing, color) && pos.sideToMove != color) return emptyList()
        return matingMoves(view)
    }

    // ------------------------------------------------------------------ motifs créés par un coup

    fun motifsAfterMove(pos: Position, move: Move): List<TacticalMotif> = motifsAfterMove(pos, move, includeMateThreat = true)

    fun motifsAfterMove(pos: Position, move: Move, includeMateThreat: Boolean): List<TacticalMotif> {
        val m = pos.normalize(move) ?: return emptyList()
        val us = pos.sideToMove
        val them = us.opposite
        val next = pos.play(m)
        val moved = next.pieceAt(m.to)!!
        val out = mutableListOf<TacticalMotif>()
        val desc = MoveDescriber.describe(pos, m)
        val label = desc.sanFrench

        if (next.isCheckmate) {
            out += TacticalMotif(MotifType.MATE_IN_ONE, "Échec et mat", "$label donne échec et mat.", listOf(m.to, next.kingSquare(them)), m)
            return out
        }
        if (next.isCheck) {
            val checkers = next.attackers(next.kingSquare(them), us)
            val direct = checkers.contains(m.to)
            val discovered = checkers.any { it != m.to }
            val text = when {
                direct && discovered -> "$label donne un échec double : le roi adverse est obligé de se déplacer."
                discovered -> "$label donne un échec à la découverte."
                else -> "$label met le roi adverse en échec."
            }
            out += TacticalMotif(MotifType.CHECK, if (discovered) "Échec à la découverte" else "Échec", text, checkers + next.kingSquare(them), m)
        }

        // Fourchette : la pièce jouée attaque au moins deux cibles intéressantes.
        val targets = next.attacksFrom(m.to).filter { s ->
            val t = next.pieceAt(s) ?: return@filter false
            if (t.color != them) return@filter false
            t.type == PieceType.KING || value(t.type) > value(moved.type) || !isDefended(next, s)
        }
        if (targets.size >= 2) {
            val names = targets.joinToString(" et ") { "${next.pieceAt(it)!!.frenchName} (${Sq.upperName(it)})" }
            val safe = see(pos, m) >= 0
            val note = if (safe) "" else " Attention : la pièce peut être capturée."
            out += TacticalMotif(
                MotifType.FORK, "Fourchette",
                "${MoveDescription.article(moved)}${moved.frenchName} en ${Sq.upperName(m.to)} attaque simultanément $names.$note",
                listOf(m.to) + targets, m,
            )
        } else if (targets.size == 1 && next.pieceAt(targets[0])!!.type != PieceType.KING) {
            val t = next.pieceAt(targets[0])!!
            if (threatOn(next.withSideToMove(us), targets[0], them) > 0 || value(t.type) > value(moved.type)) {
                out += TacticalMotif(
                    MotifType.THREATENED_PIECE, "Attaque",
                    "$label attaque ${MoveDescription.article(t).lowercase()}${t.frenchName} en ${Sq.upperName(targets[0])}.",
                    listOf(m.to, targets[0]), m,
                )
            }
        }

        // Clouages et enfilades créés par la pièce jouée (pièces à longue portée).
        out += lineMotifs(next, m.to, us).map { it.copy(move = m) }

        // Attaques découvertes : une autre pièce à longue portée voit une nouvelle cible par la case libérée.
        for ((sq, p) in next.pieces(us)) {
            if (sq == m.to || p.type !in SLIDERS) continue
            val before = pos.attacksFrom(sq).toSet()
            val newTargets = next.attacksFrom(sq).filter { s ->
                s !in before && next.pieceAt(s)?.color == them &&
                    (next.pieceAt(s)!!.type == PieceType.KING || value(next.pieceAt(s)!!.type) > value(p.type) || !isDefended(next, s))
            }
            for (s in newTargets) {
                val t = next.pieceAt(s)!!
                if (t.type == PieceType.KING) continue // déjà signalé comme échec à la découverte
                out += TacticalMotif(
                    MotifType.DISCOVERED_ATTACK, "Attaque découverte",
                    "En déplaçant ${MoveDescription.article(moved).lowercase()}${moved.frenchName}, $label découvre l'attaque de ${MoveDescription.article(p).lowercase()}${p.frenchName} (${Sq.upperName(sq)}) sur ${MoveDescription.article(t).lowercase()}${t.frenchName} en ${Sq.upperName(s)}.",
                    listOf(sq, m.from, s), m,
                )
            }
        }

        if (includeMateThreat && !next.isCheck) {
            val threats = mateThreats(next, us)
            if (threats.isNotEmpty()) {
                val sans = threats.take(2).joinToString(" ou ") { com.chessoverlay.chess.San.toSan(next.withSideToMove(us), it, true) }
                out += TacticalMotif(MotifType.MATE_THREAT, "Menace de mat", "$label menace de mater au coup suivant par $sans.", threats.map { it.to }.distinct(), m)
            }
        }
        return out
    }

    private val SLIDERS = setOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN)

    /** Clouages / enfilades exercés par la pièce à longue portée située en [sq]. */
    private fun lineMotifs(pos: Position, sq: Int, us: PieceColor): List<TacticalMotif> {
        val p = pos.pieceAt(sq) ?: return emptyList()
        if (p.type !in SLIDERS || p.color != us) return emptyList()
        val dirs = when (p.type) {
            PieceType.BISHOP -> Position.BISHOP_DIRS
            PieceType.ROOK -> Position.ROOK_DIRS
            else -> Position.QUEEN_DIRS
        }
        val out = mutableListOf<TacticalMotif>()
        val f = Sq.file(sq); val r = Sq.rank(sq)
        for (d in dirs) {
            val first = pos.firstPieceOnRay(f, r, d[0], d[1])
            if (first < 0) continue
            val a = pos.pieceAt(first)!!
            if (a.color == us) continue
            val second = pos.firstPieceOnRay(Sq.file(first), Sq.rank(first), d[0], d[1])
            if (second < 0) continue
            val b = pos.pieceAt(second)!!
            if (b.color == us) continue
            val nameA = "${MoveDescription.article(a).lowercase()}${a.frenchName} (${Sq.upperName(first)})"
            val nameB = "${MoveDescription.article(b).lowercase()}${b.frenchName} (${Sq.upperName(second)})"
            val who = "${MoveDescription.article(p)}${p.frenchName} en ${Sq.upperName(sq)}"
            when {
                a.type != PieceType.KING && b.type == PieceType.KING -> out += TacticalMotif(
                    MotifType.PIN, "Clouage absolu",
                    "$who cloue $nameA sur son roi : cette pièce ne peut pas bouger légalement hors de la ligne.",
                    listOf(sq, first, second),
                )
                a.type != PieceType.KING && value(b.type) > value(a.type) && b.type != PieceType.PAWN -> out += TacticalMotif(
                    MotifType.PIN, "Clouage relatif",
                    "$who cloue $nameA devant $nameB : si ${if (a.type.feminine) "elle" else "il"} bouge, la pièce derrière est exposée.",
                    listOf(sq, first, second),
                )
                (a.type == PieceType.KING || value(a.type) > value(p.type)) && value(a.type) > value(b.type) &&
                    b.type != PieceType.PAWN && (!isDefended(pos, second) || value(b.type) > value(p.type)) -> out += TacticalMotif(
                    MotifType.SKEWER, "Enfilade",
                    "$who attaque $nameA ; si elle s'écarte, $nameB derrière elle pourra être capturée.",
                    listOf(sq, first, second),
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------------ rapport statique complet

    fun scan(pos: Position): TacticalReport {
        val us = pos.sideToMove
        val them = us.opposite
        val legal = pos.legalMoves()
        val motifs = mutableListOf<TacticalMotif>()

        // Captures (verdict statique par SEE uniquement).
        val captures = legal.filter { pos.isCapture(it) }
            .map { staticCaptureInfo(pos, it) }
            .sortedByDescending { it.see }

        // Mats en un coup.
        val mates = legal.filter { pos.play(it).isCheckmate }
        for (m in mates) motifs += TacticalMotif(MotifType.MATE_IN_ONE, "Mat en un coup", "${com.chessoverlay.chess.San.toSan(pos, m, true)} donne échec et mat.", listOf(m.from, m.to), m)

        // Pièces adverses gagnables / non protégées.
        val winnable = pos.pieces(them).filter { it.second.type != PieceType.KING }.mapNotNull { (s, _) ->
            val best = legal.filter { pos.capturedSquare(it) == s }.maxOfOrNull { see(pos, it) }
            best?.takeIf { it > 0 }?.let { s to it }
        }
        for ((sq, gain) in winnable) {
            val t = pos.pieceAt(sq)!!
            val defended = isDefended(pos, sq)
            motifs += TacticalMotif(
                MotifType.HANGING_PIECE,
                if (defended) "Pièce adverse insuffisamment défendue" else "Pièce adverse non protégée",
                "${MoveDescription.article(t)}${t.frenchName} en ${Sq.upperName(sq)} " +
                    (if (defended) (if (t.type.feminine) "est attaquée par une pièce de moindre valeur ou plus de fois qu'elle n'est défendue" else "est attaqué par une pièce de moindre valeur ou plus de fois qu'il n'est défendu") else (if (t.type.feminine) "n'est défendue par aucune pièce" else "n'est défendu par aucune pièce")) +
                    " : gain matériel estimé ${formatCp(gain)}.",
                listOf(sq),
            )
        }

        // Nos pièces menacées.
        val threatened = threatenedPieces(pos, us)
        for ((sq, gain) in threatened) {
            val p = pos.pieceAt(sq)!!
            motifs += TacticalMotif(
                MotifType.THREATENED_PIECE, "Pièce menacée",
                "${MoveDescription.article(p)}${p.frenchName} en ${Sq.upperName(sq)} est ${if (p.type.feminine) "menacée" else "menacé"} : l'adversaire pourrait gagner ${formatCp(gain)}.",
                listOf(sq) + pos.attackers(sq, them),
            )
        }
        // Défenses possibles pour la pièce menacée la plus précieuse.
        threatened.firstOrNull()?.let { (sq, _) ->
            val p = pos.pieceAt(sq)!!
            val saving = legal.filter { m ->
                if (see(pos, m) < 0) return@filter false
                val next = pos.play(m)
                val newSq = if (m.from == sq) m.to else sq
                next.pieceAt(newSq) == p && threatOn(next, newSq, us) <= 0
            }.take(4)
            if (saving.isNotEmpty()) {
                motifs += TacticalMotif(
                    MotifType.DEFENSE, "Défendre la pièce menacée",
                    "Coups qui mettent à l'abri ou protègent ${MoveDescription.article(p).lowercase()}${p.frenchName} : " +
                        saving.joinToString(", ") { com.chessoverlay.chess.San.toSan(pos, it, true) } + ".",
                    listOf(sq) + saving.map { it.to },
                )
            }
        }

        // Menaces de mat adverses.
        val oppMates = mateThreats(pos, them)
        if (oppMates.isNotEmpty() && !pos.isCheck) {
            motifs += TacticalMotif(
                MotifType.MATE_THREAT, "Menace de mat adverse",
                "L'adversaire menace de mater par ${oppMates.take(2).joinToString(" ou ") { com.chessoverlay.chess.San.toSan(pos.withSideToMove(them), it, true) }}.",
                oppMates.map { it.to }.distinct(),
            )
            val defenses = legal.filter { m -> mateThreats(pos.play(m), them).isEmpty() && !pos.play(m).isCheckmate }
            motifs += TacticalMotif(
                MotifType.DEFENSE, "Parer le mat",
                if (defenses.isEmpty()) "Aucun coup ne pare directement cette menace." else
                    "${defenses.size} coup(s) parent la menace, par exemple : " + defenses.take(4).joinToString(", ") { com.chessoverlay.chess.San.toSan(pos, it, true) } + ".",
                defenses.take(4).map { it.to },
            )
        }

        // Motifs disponibles par coup (fourchettes, enfilades, clouages, attaques découvertes, échecs).
        val perMove = mutableListOf<TacticalMotif>()
        var checks = 0
        for (m in legal) {
            if (pos.play(m).isCheckmate) continue
            val safe = see(pos, m) >= 0
            for (mo in motifsAfterMove(pos, m, includeMateThreat = false)) {
                when (mo.type) {
                    MotifType.CHECK -> if (checks < 4) { perMove += mo; checks++ }
                    MotifType.FORK, MotifType.SKEWER, MotifType.DISCOVERED_ATTACK -> if (safe || mo.type == MotifType.DISCOVERED_ATTACK) perMove += mo
                    MotifType.PIN -> if (safe) perMove += mo.copy(title = mo.title + " possible")
                    else -> {}
                }
            }
        }
        motifs += perMove.distinctBy { it.type to it.move }.take(14)

        // Clouages déjà présents sur l'échiquier.
        for (color in listOf(us, them)) {
            for ((sq, p) in pos.pieces(color)) {
                if (p.type in SLIDERS) motifs += lineMotifs(pos, sq, color).filter { it.type == MotifType.PIN }
            }
        }
        return TacticalReport(captures, motifs.distinctBy { Triple(it.type, it.squares, it.move) })
    }

    internal fun staticCaptureInfo(pos: Position, m: Move): CaptureInfo {
        val d = MoveDescriber.describe(pos, m)
        val s = see(pos, m)
        val verdict = when {
            d.givesMate -> CaptureVerdict.FAVORABLE
            s > 50 -> CaptureVerdict.FAVORABLE
            s >= -50 -> CaptureVerdict.EQUAL
            else -> CaptureVerdict.UNFAVORABLE
        }
        return CaptureInfo(d, s, null, null, verdict, seeExplanation(pos, m, d, s), captureConsequences(pos, m, d))
    }

    internal fun seeExplanation(pos: Position, m: Move, d: MoveDescription, s: Int): String {
        val defended = pos.attackers(d.capturedSquare, d.captured!!.color).isNotEmpty()
        return when {
            s > 0 && !defended -> "${MoveDescription.article(d.captured)}${d.captured.frenchName} n'est pas ${if (d.captured.type.feminine) "protégée" else "protégé"} : la capture gagne ${formatCp(s)} à l'échange statique."
            s > 0 -> "Même après les reprises possibles, l'échange statique rapporte ${formatCp(s)}."
            s == 0 -> "Échange équilibré : le matériel gagné est rendu après la reprise."
            else -> "La pièce qui capture peut être reprise : l'échange statique perd ${formatCp(-s)}."
        }
    }

    internal fun captureConsequences(pos: Position, m: Move, d: MoveDescription): List<String> {
        val out = mutableListOf<String>()
        val next = pos.play(m)
        if (d.givesMate) out += "La capture donne échec et mat."
        else if (d.givesCheck) out += "La capture donne échec."
        val recapturers = next.attackers(m.to, pos.sideToMove.opposite)
        if (recapturers.isNotEmpty() && !d.givesMate) {
            out += "La pièce en ${Sq.upperName(m.to)} pourra être reprise par " +
                recapturers.joinToString(", ") { "${next.pieceAt(it)!!.frenchName} (${Sq.upperName(it)})" } + "."
        }
        val createdMotifs = motifsAfterMove(pos, m, includeMateThreat = false).filter { it.type != MotifType.CHECK && it.type != MotifType.MATE_IN_ONE }
        createdMotifs.take(2).forEach { out += it.description }
        val oppThreats = mateThreats(next, pos.sideToMove.opposite)
        if (!d.givesMate && oppThreats.isNotEmpty() && !next.isCheck) out += "Attention : après la capture, l'adversaire a un mat en un coup."
        return out
    }

    fun formatCp(cp: Int): String {
        val pawns = cp / 100.0
        return if (cp % 100 == 0) "${cp / 100} pion${if (kotlin.math.abs(cp) >= 200) "s" else ""}"
        else String.format(java.util.Locale.FRANCE, "%.1f pion%s", pawns, if (kotlin.math.abs(pawns) >= 2) "s" else "")
    }
}

/** Calcul d'attaques sur un tableau brut (utilisé par la SEE pour gérer les rayons X). */
internal object BoardOps {
    fun attackers(board: Array<Piece?>, sq: Int, by: PieceColor): List<Int> {
        val out = ArrayList<Int>(4)
        val f = Sq.file(sq); val r = Sq.rank(sq)
        val pr = if (by == PieceColor.WHITE) r - 1 else r + 1
        for (df in intArrayOf(-1, 1)) {
            val pf = f + df
            if (Sq.isValid(pf, pr) && board[Sq.of(pf, pr)] == Piece.of(PieceType.PAWN, by)) out += Sq.of(pf, pr)
        }
        for (d in Position.KNIGHT_DELTAS) {
            val nf = f + d[0]; val nr = r + d[1]
            if (Sq.isValid(nf, nr) && board[Sq.of(nf, nr)] == Piece.of(PieceType.KNIGHT, by)) out += Sq.of(nf, nr)
        }
        for (d in Position.KING_DELTAS) {
            val nf = f + d[0]; val nr = r + d[1]
            if (Sq.isValid(nf, nr) && board[Sq.of(nf, nr)] == Piece.of(PieceType.KING, by)) out += Sq.of(nf, nr)
        }
        fun ray(dirs: Array<IntArray>, t1: PieceType) {
            for (d in dirs) {
                var nf = f + d[0]; var nr = r + d[1]
                while (Sq.isValid(nf, nr)) {
                    val p = board[Sq.of(nf, nr)]
                    if (p != null) {
                        if (p.color == by && (p.type == t1 || p.type == PieceType.QUEEN)) out += Sq.of(nf, nr)
                        break
                    }
                    nf += d[0]; nr += d[1]
                }
            }
        }
        ray(Position.BISHOP_DIRS, PieceType.BISHOP)
        ray(Position.ROOK_DIRS, PieceType.ROOK)
        return out
    }
}
